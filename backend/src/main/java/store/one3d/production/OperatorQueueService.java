package store.one3d.production;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.PageResponse;
import store.one3d.orders.OrderResponse;
import store.one3d.orders.OrderService;
import store.one3d.orders.OrderStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What operators need to see: orders by state, each with its job. */
@Service
public class OperatorQueueService {

    private final OrderService orderService;
    private final ProductionJobRepository jobs;

    OperatorQueueService(OrderService orderService, ProductionJobRepository jobs) {
        this.orderService = orderService;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueItem> queue(Collection<OrderStatus> statuses, int page, int size) {
        PageResponse<OrderResponse> orders = orderService.listByStatus(statuses, page, size);
        List<UUID> orderIds = orders.items().stream().map(OrderResponse::id).toList();
        Map<UUID, ProductionJob> jobByOrder = jobs.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(ProductionJob::getOrderId, Function.identity()));
        List<QueueItem> items = orders.items().stream()
                .map(order -> item(order, jobByOrder.get(order.id())))
                .toList();
        return new PageResponse<>(items, orders.page(), orders.size(), orders.totalItems(), orders.totalPages());
    }

    @Transactional(readOnly = true)
    public QueueItem order(UUID orderId) {
        OrderResponse order = orderService.getOrder(orderId);
        return item(order, jobs.findByOrderId(orderId).orElse(null));
    }

    private static QueueItem item(OrderResponse order, ProductionJob job) {
        return new QueueItem(order, job == null ? null : job.getId(), job == null ? null : job.getStatus());
    }
}
