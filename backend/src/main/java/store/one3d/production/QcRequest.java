package store.one3d.production;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/ops/attempts/{attemptId}/qc. Notes are optional. */
record QcRequest(@NotNull QcResult result, @Size(max = 500) String notes) {
}
