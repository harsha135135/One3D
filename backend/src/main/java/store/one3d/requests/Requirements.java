package store.one3d.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** One submission: a checked model file and what the customer wants made from it. */
record Requirements(
        @NotNull UUID artifactId,
        @NotBlank @Size(max = 80) String material,
        @NotBlank @Size(max = 80) String colour,
        @Size(max = 1000) String notes) {
}
