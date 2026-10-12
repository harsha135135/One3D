package store.one3d.quotes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * An operator's quote for the latest revision: the price (INR, minor units),
 * how long it stays open, and the recipe it will be made with.
 */
record IssueQuoteRequest(
        @NotNull UUID revisionId,
        @NotNull @Positive @Max(100_000_000) Long priceMinor,
        @NotNull @Min(1) @Max(14) Integer validDays,
        @NotBlank @Size(max = 80) String material,
        @NotNull @Min(50) @Max(400) Integer layerHeightMicrons,
        @NotNull @Size(max = 500) String recipeNotes) {
}
