package store.one3d.catalog;

import store.one3d.common.Money;

import java.util.UUID;

/** API representation of an orderable catalog configuration. */
record CatalogItemResponse(
        UUID configurationId,
        String productName,
        String configurationCode,
        String description,
        Money price,
        int recipeRevisionNumber) {

    static CatalogItemResponse from(CatalogSnapshot item) {
        return new CatalogItemResponse(
                item.configurationId(),
                item.productName(),
                item.configurationCode(),
                item.configurationDescription(),
                new Money(item.unitPriceMinor(), item.currency()),
                item.recipeRevisionNumber());
    }
}
