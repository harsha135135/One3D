package store.one3d.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One approved, append-only revision of manufacturing instructions. A change
 * creates a new revision instead of editing this one. It belongs either to a
 * catalog configuration or to the quote that priced a customer's own model
 * (V7 allows exactly one owner).
 */
@Entity
@Table(name = "recipe_revision")
@Immutable
class RecipeRevision {

    @Id
    private UUID id;

    @Column(name = "product_configuration_id", updatable = false)
    private UUID productConfigurationId;

    @Column(name = "quote_id", updatable = false)
    private UUID quoteId;

    @Column(name = "revision_number")
    private int revisionNumber;

    private String material;

    @Column(name = "layer_height_microns")
    private int layerHeightMicrons;

    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    protected RecipeRevision() {
        // for JPA
    }

    /** The recipe a quote prices. A quote has exactly one, so it is revision 1. */
    static RecipeRevision forQuote(UUID quoteId, String material, int layerHeightMicrons, String notes, Instant now) {
        RecipeRevision recipe = new RecipeRevision();
        recipe.id = UUID.randomUUID();
        recipe.quoteId = quoteId;
        recipe.revisionNumber = 1;
        recipe.material = material;
        recipe.layerHeightMicrons = layerHeightMicrons;
        recipe.notes = notes;
        recipe.createdAt = now;
        return recipe;
    }

    RecipeSnapshot toSnapshot() {
        return new RecipeSnapshot(id, revisionNumber, material, layerHeightMicrons, notes);
    }

    UUID getId() {
        return id;
    }

    UUID getQuoteId() {
        return quoteId;
    }

    int getRevisionNumber() {
        return revisionNumber;
    }
}
