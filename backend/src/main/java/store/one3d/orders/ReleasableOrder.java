package store.one3d.orders;

import java.util.UUID;

/** What production needs from a confirmed order to release it. */
public record ReleasableOrder(UUID orderId, UUID recipeRevisionId) {
}
