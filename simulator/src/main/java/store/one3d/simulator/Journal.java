package store.one3d.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The simulator's durable state: one SQLite file. Every change is written in a
 * transaction and committed before the simulator answers, so a restart (or a
 * crash after the commit) never loses or repeats an accepted command.
 *
 * <p>Requests are handled one at a time (single-threaded server), so a single
 * connection is enough.
 */
final class Journal implements AutoCloseable {

    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Connection connection;

    Journal(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = FULL");
                statement.execute("PRAGMA foreign_keys = ON");
            }
            createSchema();
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Cannot open journal " + file, e);
        }
    }

    /** Runs the work in one transaction: commit on success, roll back on any exception. */
    <T> T inTransaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Journal operation failed", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // One row per virtual printer. bed_cycle_attempt_id names the attempt
            // whose print occupies the bed; a clearance must name it to be used.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS printer (
                        printer_id           TEXT PRIMARY KEY,
                        running_attempt_id   TEXT,
                        bed_state            TEXT NOT NULL CHECK (bed_state IN ('CLEAR', 'OCCUPIED')),
                        bed_cycle_attempt_id TEXT,
                        drop_next_start_ack  INTEGER NOT NULL DEFAULT 0
                    )""");
            // Every start command received, with the outcome decided for it.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS command (
                        command_id         TEXT PRIMARY KEY,
                        printer_id         TEXT NOT NULL REFERENCES printer (printer_id),
                        attempt_id         TEXT NOT NULL,
                        job_id             TEXT NOT NULL,
                        payload_hash       TEXT NOT NULL,
                        cleared_attempt_id TEXT,
                        outcome            TEXT NOT NULL
                            CHECK (outcome IN ('STARTED', 'REJECTED_BUSY', 'REJECTED_BED_NOT_CLEAR')),
                        received_at        TEXT NOT NULL
                    )""");
            // One execution per started attempt; a second start for the same
            // attempt is impossible (primary key).
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS execution (
                        attempt_id     TEXT PRIMARY KEY,
                        command_id     TEXT NOT NULL UNIQUE REFERENCES command (command_id),
                        printer_id     TEXT NOT NULL REFERENCES printer (printer_id),
                        phase          TEXT NOT NULL CHECK (phase IN ('RUNNING', 'COMPLETED', 'FAILED')),
                        failure_reason TEXT,
                        started_at     TEXT NOT NULL,
                        ended_at       TEXT
                    )""");
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close journal", e);
        }
    }
}
