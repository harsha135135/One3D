package store.one3d.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Current time for stored timestamps. */
public final class Timestamps {

    private Timestamps() {
    }

    /**
     * PostgreSQL stores microseconds; truncating keeps a value returned right
     * after saving identical to what a later read returns.
     */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
