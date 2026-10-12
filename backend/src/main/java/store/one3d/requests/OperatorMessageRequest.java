package store.one3d.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the operator tells the customer when asking for changes or rejecting. */
record OperatorMessageRequest(@NotBlank @Size(max = 1000) String message) {
}
