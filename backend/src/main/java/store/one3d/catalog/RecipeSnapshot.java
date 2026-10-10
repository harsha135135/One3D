package store.one3d.catalog;

import java.util.UUID;

/** An approved recipe revision as other modules see it. */
public record RecipeSnapshot(UUID id, int revisionNumber, String material, int layerHeightMicrons, String notes) {
}
