package store.one3d.common;

import java.util.Map;
import java.util.Optional;

/**
 * Database constraints that can be hit when two requests race, and the 409
 * code each one means. The code checks these rules first; the constraint is
 * the guarantee when two transactions pass those checks at the same time.
 */
final class ConstraintConflicts {

    private static final Map<String, String> CODES = Map.of(
            "production_job_order_unique", "ALREADY_RELEASED",
            "print_attempt_one_active_per_printer", "PRINTER_BUSY",
            "print_attempt_one_active_per_job", "ATTEMPT_ALREADY_ACTIVE",
            "print_attempt_number_unique", "CONCURRENT_MODIFICATION",
            "bed_clearance_cleared_attempt_unique", "BED_ALREADY_CLEAR",
            "bed_clearance_consumed_by_unique", "CONCURRENT_MODIFICATION",
            "bed_clearance_one_open_per_printer", "CONCURRENT_MODIFICATION");

    private ConstraintConflicts() {
    }

    /** The conflict code for a constraint named anywhere in the exception chain, if known. */
    static Optional<String> codeFor(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : CODES.entrySet()) {
                if (message.contains(entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
