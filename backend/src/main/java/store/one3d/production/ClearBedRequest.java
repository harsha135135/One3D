package store.one3d.production;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body of POST /api/ops/printers/{printerId}/clear-bed: the attempt whose print
 * was removed. Naming it keeps a delayed request from clearing a later print.
 */
record ClearBedRequest(@NotNull UUID attemptId) {
}
