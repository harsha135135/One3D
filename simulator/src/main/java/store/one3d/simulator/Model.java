package store.one3d.simulator;

/** Request and response shapes of the simulator's HTTP API. */
final class Model {

    private Model() {
    }

    enum Outcome { STARTED, REJECTED_BUSY, REJECTED_BED_NOT_CLEAR }

    enum Phase { RUNNING, COMPLETED, FAILED }

    enum BedState { CLEAR, OCCUPIED }

    /**
     * A request to start one attempt. {@code clearedAttemptId} names the print
     * whose removal the operator confirmed (the bed cycle being cleared); it is
     * null when the backend believes the bed was never used.
     */
    record StartCommand(String commandId, String printerId, String attemptId, String jobId,
                        String payloadHash, String clearedAttemptId) {
    }

    /** The decision for a start command. Repeating the command returns the same decision. */
    record StartResult(String commandId, String attemptId, Outcome outcome, boolean simulated) {
    }

    /** What the simulator knows about a command: its outcome and, if started, the execution phase. */
    record CommandView(String commandId, String printerId, String attemptId, Outcome outcome,
                       Phase phase, String failureReason, boolean simulated) {
    }

    record PrinterView(String printerId, String runningAttemptId, BedState bedState,
                       String bedCycleAttemptId, boolean simulated) {
    }

    record FailRequest(String reason) {
    }

    record FaultRequest(boolean dropNextStartAck) {
    }
}
