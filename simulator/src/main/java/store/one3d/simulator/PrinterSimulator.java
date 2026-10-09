package store.one3d.simulator;

import store.one3d.simulator.Model.BedState;
import store.one3d.simulator.Model.CommandView;
import store.one3d.simulator.Model.Outcome;
import store.one3d.simulator.Model.Phase;
import store.one3d.simulator.Model.PrinterView;
import store.one3d.simulator.Model.StartCommand;
import store.one3d.simulator.Model.StartResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Simulated printers and their rules. All state lives in the {@link Journal};
 * each method is one SQLite transaction.
 *
 * <p>Printers are keyed by id and created on first use with a cleared bed. The
 * V1 demonstration uses a single printer; keying by id lets automated tests
 * share one simulator process without affecting each other.
 */
final class PrinterSimulator {

    /** A start decision plus whether this reply should be dropped (fault injection). */
    record StartDecision(StartResult result, boolean dropAcknowledgement) {
    }

    private record PrinterRow(String runningAttemptId, BedState bedState, String bedCycleAttemptId,
                              boolean dropNextStartAck) {
    }

    private final Journal journal;
    private final Clock clock;

    PrinterSimulator(Journal journal, Clock clock) {
        this.journal = journal;
        this.clock = clock;
    }

    /**
     * Decides and journals a start command before anything is acknowledged.
     * <ul>
     *   <li>Same command id and payload: the stored decision, nothing new happens.</li>
     *   <li>Same command id, different payload: rejected as a conflict.</li>
     *   <li>Printer running another attempt: {@code REJECTED_BUSY}.</li>
     *   <li>Bed occupied and the command does not clear exactly that print:
     *       {@code REJECTED_BED_NOT_CLEAR}. A clearance for an older print is refused.</li>
     *   <li>Otherwise the execution starts and its print occupies the bed.</li>
     * </ul>
     */
    StartDecision start(StartCommand command) {
        validate(command);
        return journal.inTransaction(connection -> {
            PrinterRow printer = printerForUpdate(connection, command.printerId());
            boolean drop = consumeDropFault(connection, command.printerId(), printer);

            Optional<StartResult> previous = storedResult(connection, command);
            if (previous.isPresent()) {
                return new StartDecision(previous.get(), drop);
            }

            Outcome outcome = decide(printer, command);
            insertCommand(connection, command, outcome);
            if (outcome == Outcome.STARTED) {
                startExecution(connection, command);
            }
            return new StartDecision(new StartResult(command.commandId(), command.attemptId(), outcome, true), drop);
        });
    }

    /** Ends the running execution successfully. The finished part stays on the bed. */
    PrinterView complete(String printerId) {
        return endExecution(printerId, Phase.COMPLETED, null);
    }

    /** Ends the running execution as failed. The failed output stays on the bed. */
    PrinterView fail(String printerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SimulatorException(400, "INVALID_REQUEST", "A failure needs a reason.");
        }
        return endExecution(printerId, Phase.FAILED, reason);
    }

    /** Makes the next start reply for this printer get lost after the command is journaled. */
    void dropNextStartAcknowledgement(String printerId, boolean drop) {
        journal.inTransaction(connection -> {
            printerForUpdate(connection, printerId);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE printer SET drop_next_start_ack = ? WHERE printer_id = ?")) {
                update.setInt(1, drop ? 1 : 0);
                update.setString(2, printerId);
                update.executeUpdate();
            }
            return null;
        });
    }

    Optional<CommandView> findCommand(String commandId) {
        return journal.inTransaction(connection -> {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT c.command_id, c.printer_id, c.attempt_id, c.outcome, e.phase, e.failure_reason
                    FROM command c LEFT JOIN execution e ON e.command_id = c.command_id
                    WHERE c.command_id = ?""")) {
                query.setString(1, commandId);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) {
                        return Optional.empty();
                    }
                    String phase = row.getString("phase");
                    return Optional.of(new CommandView(
                            row.getString("command_id"),
                            row.getString("printer_id"),
                            row.getString("attempt_id"),
                            Outcome.valueOf(row.getString("outcome")),
                            phase == null ? null : Phase.valueOf(phase),
                            row.getString("failure_reason"),
                            true));
                }
            }
        });
    }

    Optional<PrinterView> findPrinter(String printerId) {
        return journal.inTransaction(connection -> existingPrinter(connection, printerId)
                .map(row -> view(printerId, row)));
    }

    /** Number of executions ever started for an attempt (0 or 1), for duplicate-delivery checks. */
    int executionCount(String attemptId) {
        return journal.inTransaction(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT count(*) FROM execution WHERE attempt_id = ?")) {
                query.setString(1, attemptId);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    return row.getInt(1);
                }
            }
        });
    }

    private static Outcome decide(PrinterRow printer, StartCommand command) {
        if (printer.runningAttemptId() != null) {
            return Outcome.REJECTED_BUSY;
        }
        boolean clearsCurrentPrint = printer.bedState() == BedState.CLEAR
                || Objects.equals(command.clearedAttemptId(), printer.bedCycleAttemptId());
        return clearsCurrentPrint ? Outcome.STARTED : Outcome.REJECTED_BED_NOT_CLEAR;
    }

    private Optional<StartResult> storedResult(Connection connection, StartCommand command) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT attempt_id, payload_hash, outcome FROM command WHERE command_id = ?")) {
            query.setString(1, command.commandId());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                if (!row.getString("payload_hash").equals(command.payloadHash())) {
                    throw new SimulatorException(409, "PAYLOAD_MISMATCH",
                            "Command " + command.commandId() + " was already received with a different payload.");
                }
                return Optional.of(new StartResult(command.commandId(), row.getString("attempt_id"),
                        Outcome.valueOf(row.getString("outcome")), true));
            }
        }
    }

    private void insertCommand(Connection connection, StartCommand command, Outcome outcome) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO command (command_id, printer_id, attempt_id, job_id, payload_hash,
                                     cleared_attempt_id, outcome, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            insert.setString(1, command.commandId());
            insert.setString(2, command.printerId());
            insert.setString(3, command.attemptId());
            insert.setString(4, command.jobId());
            insert.setString(5, command.payloadHash());
            insert.setString(6, command.clearedAttemptId());
            insert.setString(7, outcome.name());
            insert.setString(8, clock.instant().toString());
            insert.executeUpdate();
        }
    }

    private void startExecution(Connection connection, StartCommand command) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO execution (attempt_id, command_id, printer_id, phase, started_at)
                VALUES (?, ?, ?, 'RUNNING', ?)""")) {
            insert.setString(1, command.attemptId());
            insert.setString(2, command.commandId());
            insert.setString(3, command.printerId());
            insert.setString(4, clock.instant().toString());
            insert.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new SimulatorException(409, "ATTEMPT_ALREADY_STARTED",
                        "Attempt " + command.attemptId() + " was already started by another command.");
            }
            throw e;
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE printer SET running_attempt_id = ?, bed_state = 'OCCUPIED', bed_cycle_attempt_id = ?
                WHERE printer_id = ?""")) {
            update.setString(1, command.attemptId());
            update.setString(2, command.attemptId());
            update.setString(3, command.printerId());
            update.executeUpdate();
        }
    }

    private PrinterView endExecution(String printerId, Phase phase, String reason) {
        return journal.inTransaction(connection -> {
            PrinterRow printer = existingPrinter(connection, printerId).orElseThrow(() ->
                    new SimulatorException(404, "PRINTER_NOT_FOUND", "Printer " + printerId + " is unknown."));
            if (printer.runningAttemptId() == null) {
                throw new SimulatorException(409, "NOT_RUNNING", "Printer " + printerId + " is not running anything.");
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE execution SET phase = ?, failure_reason = ?, ended_at = ?
                    WHERE attempt_id = ? AND phase = 'RUNNING'""")) {
                update.setString(1, phase.name());
                update.setString(2, reason);
                update.setString(3, clock.instant().toString());
                update.setString(4, printer.runningAttemptId());
                update.executeUpdate();
            }
            // The printer becomes idle; the bed stays occupied by the same print.
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE printer SET running_attempt_id = NULL WHERE printer_id = ?")) {
                update.setString(1, printerId);
                update.executeUpdate();
            }
            return view(printerId, existingPrinter(connection, printerId).orElseThrow());
        });
    }

    private PrinterRow printerForUpdate(Connection connection, String printerId) throws SQLException {
        Optional<PrinterRow> existing = existingPrinter(connection, printerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO printer (printer_id, bed_state) VALUES (?, 'CLEAR')")) {
            insert.setString(1, printerId);
            insert.executeUpdate();
        }
        return new PrinterRow(null, BedState.CLEAR, null, false);
    }

    private static boolean consumeDropFault(Connection connection, String printerId, PrinterRow printer)
            throws SQLException {
        if (!printer.dropNextStartAck()) {
            return false;
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE printer SET drop_next_start_ack = 0 WHERE printer_id = ?")) {
            update.setString(1, printerId);
            update.executeUpdate();
        }
        return true;
    }

    private static Optional<PrinterRow> existingPrinter(Connection connection, String printerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT running_attempt_id, bed_state, bed_cycle_attempt_id, drop_next_start_ack
                FROM printer WHERE printer_id = ?""")) {
            query.setString(1, printerId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PrinterRow(
                        row.getString("running_attempt_id"),
                        BedState.valueOf(row.getString("bed_state")),
                        row.getString("bed_cycle_attempt_id"),
                        row.getInt("drop_next_start_ack") == 1));
            }
        }
    }

    private static PrinterView view(String printerId, PrinterRow row) {
        return new PrinterView(printerId, row.runningAttemptId(), row.bedState(), row.bedCycleAttemptId(), true);
    }

    private static void validate(StartCommand command) {
        if (isBlank(command.commandId()) || isBlank(command.printerId()) || isBlank(command.attemptId())
                || isBlank(command.jobId()) || isBlank(command.payloadHash())) {
            throw new SimulatorException(400, "INVALID_REQUEST",
                    "commandId, printerId, attemptId, jobId and payloadHash are required.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
