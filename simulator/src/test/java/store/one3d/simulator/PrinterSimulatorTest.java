package store.one3d.simulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import store.one3d.simulator.Model.BedState;
import store.one3d.simulator.Model.Outcome;
import store.one3d.simulator.Model.Phase;
import store.one3d.simulator.Model.StartCommand;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The simulator's rules against a real SQLite journal in a temporary directory. */
class PrinterSimulatorTest {

    @TempDir
    Path dir;

    private Journal journal;
    private PrinterSimulator simulator;
    private final String printer = "printer-" + UUID.randomUUID();

    @BeforeEach
    void open() {
        journal = new Journal(dir.resolve("journal.db"));
        simulator = new PrinterSimulator(journal, Clock.systemUTC());
    }

    @AfterEach
    void close() {
        journal.close();
    }

    @Test
    void aStartIsJournaledAndOccupiesTheBed() {
        StartCommand command = start("attempt-1", null);

        assertThat(simulator.start(command).result().outcome()).isEqualTo(Outcome.STARTED);

        assertThat(simulator.findCommand(command.commandId())).get()
                .satisfies(view -> {
                    assertThat(view.outcome()).isEqualTo(Outcome.STARTED);
                    assertThat(view.phase()).isEqualTo(Phase.RUNNING);
                });
        assertThat(simulator.findPrinter(printer)).get().satisfies(view -> {
            assertThat(view.runningAttemptId()).isEqualTo("attempt-1");
            assertThat(view.bedState()).isEqualTo(BedState.OCCUPIED);
            assertThat(view.bedCycleAttemptId()).isEqualTo("attempt-1");
        });
    }

    @Test
    void aRepeatedCommandReturnsTheSameDecisionAndStartsOnlyOnce() {
        StartCommand command = start("attempt-1", null);
        simulator.start(command);

        assertThat(simulator.start(command).result().outcome()).isEqualTo(Outcome.STARTED);
        assertThat(simulator.executionCount("attempt-1")).isEqualTo(1);
    }

    @Test
    void theSameCommandIdWithADifferentPayloadIsRefused() {
        StartCommand command = start("attempt-1", null);
        simulator.start(command);
        StartCommand altered = new StartCommand(command.commandId(), printer, "attempt-1", "job-1", "other-hash", null);

        assertThatThrownBy(() -> simulator.start(altered))
                .isInstanceOf(SimulatorException.class)
                .extracting("code").isEqualTo("PAYLOAD_MISMATCH");
    }

    @Test
    void aBusyPrinterRefusesAnotherAttemptAndReplaysThatRefusal() {
        simulator.start(start("attempt-1", null));
        StartCommand second = start("attempt-2", null);

        assertThat(simulator.start(second).result().outcome()).isEqualTo(Outcome.REJECTED_BUSY);
        assertThat(simulator.start(second).result().outcome()).isEqualTo(Outcome.REJECTED_BUSY);
        assertThat(simulator.executionCount("attempt-2")).isZero();
    }

    @Test
    void aFinishedPrintKeepsTheBedOccupiedUntilAClearanceNamesIt() {
        simulator.start(start("attempt-1", null));
        simulator.complete(printer);

        assertThat(simulator.findPrinter(printer)).get().satisfies(view -> {
            assertThat(view.runningAttemptId()).isNull();
            assertThat(view.bedState()).isEqualTo(BedState.OCCUPIED);
        });
        assertThat(simulator.start(start("attempt-2", null)).result().outcome())
                .isEqualTo(Outcome.REJECTED_BED_NOT_CLEAR);
        assertThat(simulator.start(start("attempt-3", "attempt-1")).result().outcome())
                .isEqualTo(Outcome.STARTED);
    }

    @Test
    void aClearanceForAnEarlierPrintCannotClearALaterOne() {
        simulator.start(start("attempt-1", null));
        simulator.fail(printer, "Warping");
        simulator.start(start("attempt-2", "attempt-1"));   // clearance for print 1 used here
        simulator.complete(printer);                        // print 2 now occupies the bed

        // A delayed request still carrying the clearance for print 1.
        assertThat(simulator.start(start("attempt-3", "attempt-1")).result().outcome())
                .isEqualTo(Outcome.REJECTED_BED_NOT_CLEAR);
        assertThat(simulator.findPrinter(printer)).get().satisfies(view -> {
            assertThat(view.bedState()).isEqualTo(BedState.OCCUPIED);
            assertThat(view.bedCycleAttemptId()).isEqualTo("attempt-2");
        });
    }

    @Test
    void failureIsRecordedWithItsReason() {
        StartCommand command = start("attempt-1", null);
        simulator.start(command);

        simulator.fail(printer, "Nozzle clog");

        assertThat(simulator.findCommand(command.commandId())).get().satisfies(view -> {
            assertThat(view.phase()).isEqualTo(Phase.FAILED);
            assertThat(view.failureReason()).isEqualTo("Nozzle clog");
        });
    }

    @Test
    void stateSurvivesARestartOnTheSameJournal() {
        StartCommand command = start("attempt-1", null);
        simulator.start(command);
        journal.close();

        journal = new Journal(dir.resolve("journal.db"));
        simulator = new PrinterSimulator(journal, Clock.systemUTC());

        assertThat(simulator.findCommand(command.commandId())).get()
                .extracting(Model.CommandView::phase).isEqualTo(Phase.RUNNING);
        assertThat(simulator.start(command).result().outcome()).isEqualTo(Outcome.STARTED);
        assertThat(simulator.executionCount("attempt-1")).isEqualTo(1);
    }

    @Test
    void endingWhenNothingRunsIsRefused() {
        simulator.start(start("attempt-1", null));
        simulator.complete(printer);

        assertThatThrownBy(() -> simulator.complete(printer))
                .isInstanceOf(SimulatorException.class)
                .extracting("code").isEqualTo("NOT_RUNNING");
    }

    private StartCommand start(String attemptId, String clearedAttemptId) {
        return new StartCommand("command-" + attemptId, printer, attemptId, "job-1", "hash-" + attemptId, clearedAttemptId);
    }
}
