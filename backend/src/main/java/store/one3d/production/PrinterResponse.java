package store.one3d.production;

import java.time.Instant;
import java.util.UUID;

/** API representation of a printer: who owns it and whether its bed is clear. */
public record PrinterResponse(
        UUID id,
        String name,
        boolean simulated,
        BedState bedState,
        UUID currentAttemptId,
        Instant bedStateChangedAt) {

    static PrinterResponse from(Printer printer) {
        return new PrinterResponse(
                printer.getId(),
                printer.getName(),
                printer.isSimulated(),
                printer.getBedState(),
                printer.getCurrentAttemptId(),
                printer.getBedStateChangedAt());
    }
}
