package store.one3d.production;

/** What the backend knows about delivering a start command to the simulator. */
enum CommandStatus {
    /** Recorded, not yet confirmed by the simulator. */
    PENDING,
    /** The simulator's journal shows the command was received and started. */
    ACKNOWLEDGED,
    /** The simulator received the command and refused it. */
    REJECTED,
    /** Delivery outcome unknown (lost reply, timeout, simulator unreachable). */
    UNCERTAIN
}
