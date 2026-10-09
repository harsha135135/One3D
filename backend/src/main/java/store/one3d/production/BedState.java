package store.one3d.production;

/**
 * Whether the print bed is free for the next print. Tracked separately from
 * whether the printer is executing: an idle printer can still have an occupied bed.
 */
public enum BedState {
    CLEAR,
    OCCUPIED
}
