package store.one3d.production;

/**
 * What one delivery of a start command established.
 *
 * @param reason the refusal reason or the error, for the record
 */
record DispatchResult(Kind kind, String reason) {

    enum Kind {
        /** The simulator's journal shows the attempt started. */
        STARTED,
        /** The simulator received the command and refused it; nothing printed. */
        REFUSED,
        /** No answer: the start may or may not have happened. */
        UNKNOWN
    }

    static DispatchResult from(SimulatorClient.CommandReport report) {
        return report.started()
                ? new DispatchResult(Kind.STARTED, null)
                : new DispatchResult(Kind.REFUSED, report.outcome());
    }

    static DispatchResult unknown(String error) {
        return new DispatchResult(Kind.UNKNOWN, error);
    }
}
