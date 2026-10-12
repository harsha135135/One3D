package store.one3d.orders;

import java.time.Instant;

/** One fact on an order's timeline, as the customer sees it. */
public record TimelineEntry(OrderFactKind kind, Instant occurredAt, String detail) {
}
