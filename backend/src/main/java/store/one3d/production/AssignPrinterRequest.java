package store.one3d.production;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of POST /api/ops/jobs/{jobId}/attempts. */
record AssignPrinterRequest(@NotNull UUID printerId) {
}
