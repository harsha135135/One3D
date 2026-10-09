package store.one3d.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/ops/attempts/{attemptId}/simulate-failure. */
record SimulatedFailureRequest(@NotBlank @Size(max = 500) String reason) {
}
