package store.one3d.production;

import store.one3d.orders.OrderResponse;

import java.util.UUID;

/** An order in the operator queue, with its production job if it has been released. */
public record QueueItem(OrderResponse order, UUID jobId, JobStatus jobStatus) {
}
