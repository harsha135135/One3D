package store.one3d.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import store.one3d.catalog.CatalogSnapshot;

import java.util.UUID;

/**
 * The single line of an order: a snapshot of the catalog at order time.
 * {@code @Immutable} tells Hibernate never to issue UPDATE statements for it.
 */
@Entity
@Table(name = "order_line")
@Immutable
class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    private LineSource source;

    @Column(name = "product_configuration_id")
    private UUID productConfigurationId;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "recipe_revision_id")
    private UUID recipeRevisionId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "configuration_code")
    private String configurationCode;

    @Column(name = "configuration_description")
    private String configurationDescription;

    @Column(name = "recipe_revision_number")
    private int recipeRevisionNumber;

    private int quantity;

    @Column(name = "unit_price_minor")
    private long unitPriceMinor;

    private String currency;

    protected OrderLine() {
        // for JPA
    }

    OrderLine(CustomerOrder order, CatalogSnapshot item, int quantity) {
        this.order = order;
        this.source = LineSource.CATALOG;
        this.productConfigurationId = item.configurationId();
        this.recipeRevisionId = item.recipeRevisionId();
        this.productName = item.productName();
        this.configurationCode = item.configurationCode();
        this.configurationDescription = item.configurationDescription();
        this.recipeRevisionNumber = item.recipeRevisionNumber();
        this.quantity = quantity;
        this.unitPriceMinor = item.unitPriceMinor();
        this.currency = item.currency();
    }

    LineSource getSource() {
        return source;
    }

    UUID getQuoteId() {
        return quoteId;
    }

    UUID getProductConfigurationId() {
        return productConfigurationId;
    }

    UUID getRecipeRevisionId() {
        return recipeRevisionId;
    }

    String getProductName() {
        return productName;
    }

    String getConfigurationCode() {
        return configurationCode;
    }

    String getConfigurationDescription() {
        return configurationDescription;
    }

    int getRecipeRevisionNumber() {
        return recipeRevisionNumber;
    }

    int getQuantity() {
        return quantity;
    }

    long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    String getCurrency() {
        return currency;
    }
}
