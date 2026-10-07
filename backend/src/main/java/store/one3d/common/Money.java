package store.one3d.common;

/**
 * An amount in integer minor units (for INR, paise) with its ISO 4217 currency
 * code. Integers avoid floating-point rounding errors in prices.
 */
public record Money(long amountMinor, String currency) {
}
