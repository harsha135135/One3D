package store.one3d.production;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import store.one3d.common.PageResponse;
import store.one3d.orders.OrderStatus;

import java.util.List;
import java.util.UUID;

/** The operator queue: any customer's orders, by state. Operators only (SecurityConfig). */
@RestController
@RequestMapping("/api/ops/orders")
class OperatorOrdersController {

    private final OperatorQueueService queueService;

    OperatorOrdersController(OperatorQueueService queueService) {
        this.queueService = queueService;
    }

    /** Oldest first. Defaults to CONFIRMED (waiting for release or in production). */
    @GetMapping
    PageResponse<QueueItem> queue(@RequestParam(defaultValue = "CONFIRMED") List<OrderStatus> status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return queueService.queue(status, page, size);
    }

    @GetMapping("/{orderId}")
    QueueItem order(@PathVariable UUID orderId) {
        return queueService.order(orderId);
    }
}
