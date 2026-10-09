package store.one3d.production;

import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a printer: which attempt owns it, whether its bed is
 * clear, and if not, whose print is on it (the attempt a clearance must name).
 */
public record PrinterResponse(
        UUID id,
        String name,
        boolean simulated,
        BedState bedState,
        UUID bedCycleAttemptId,
        UUID currentAttemptId,
        Instant bedStateChangedAt) {

    static PrinterResponse from(Printer printer) {
        return new PrinterResponse(
                printer.getId(),
                printer.getName(),
                printer.isSimulated(),
                printer.getBedState(),
                printer.getBedCycleAttemptId(),
                printer.getCurrentAttemptId(),
                printer.getBedStateChangedAt());
    }
}
