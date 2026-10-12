package store.one3d.common;

import java.util.Map;
import java.util.Optional;

/**
 * Database constraints that can be hit when two requests race, and the 409
 * code each one means. The code checks these rules first; the constraint is
 * the guarantee when two transactions pass those checks at the same time.
 */
final class ConstraintConflicts {

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("production_job_order_unique", "ALREADY_RELEASED"),
            Map.entry("print_attempt_one_active_per_printer", "PRINTER_BUSY"),
            Map.entry("print_attempt_one_active_per_job", "ATTEMPT_ALREADY_ACTIVE"),
            Map.entry("print_attempt_number_unique", "CONCURRENT_MODIFICATION"),
            Map.entry("bed_clearance_cleared_attempt_unique", "BED_ALREADY_CLEAR"),
            Map.entry("bed_clearance_consumed_by_unique", "CONCURRENT_MODIFICATION"),
            Map.entry("bed_clearance_one_open_per_printer", "CONCURRENT_MODIFICATION"),
            Map.entry("submission_revision_artifact_unique", "FILE_ALREADY_SUBMITTED"),
            Map.entry("submission_revision_number_unique", "CONCURRENT_MODIFICATION"),
            Map.entry("quote_one_issued_per_request", "CONCURRENT_MODIFICATION"),
            Map.entry("order_line_quote_unique", "QUOTE_ALREADY_ACCEPTED"),
            Map.entry("quote_order_unique", "QUOTE_ALREADY_ACCEPTED"),
            Map.entry("pending_work_one_per_artifact", "CONCURRENT_MODIFICATION"));

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
