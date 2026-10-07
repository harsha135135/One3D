package store.one3d.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** A sellable configuration of a product, with its current price and recipe. */
@Entity
@Table(name = "product_configuration")
@Immutable
class ProductConfiguration {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private String code;

    private String description;

    @Column(name = "price_minor")
    private long priceMinor;

    private String currency;

    /** Null until a recipe revision is approved; such a configuration is not orderable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_recipe_revision_id")
    private RecipeRevision currentRecipeRevision;

    protected ProductConfiguration() {
        // for JPA
    }

    /** Copies the values an order needs. Call inside a transaction (lazy associations). */
    CatalogSnapshot toSnapshot() {
        return new CatalogSnapshot(
                id,
                product.getName(),
                code,
                description,
                currentRecipeRevision.getId(),
                currentRecipeRevision.getRevisionNumber(),
                priceMinor,
                currency);
    }
}
