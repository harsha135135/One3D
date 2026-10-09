package store.one3d.orders;

/** Where an order line's item and recipe come from. */
public enum LineSource {
    /** A catalog configuration and its current recipe revision at order time. */
    CATALOG,
    /** An accepted quote for a customer's own model, with the recipe that quote priced. */
    CUSTOM_QUOTE
}
