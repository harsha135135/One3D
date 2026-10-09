package store.one3d.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import store.one3d.catalog.CatalogService;
import store.one3d.catalog.CatalogSnapshot;
import store.one3d.common.PageResponse;
import store.one3d.common.Timestamps;

import java.util.Collection;
import java.util.UUID;

/** Order use cases. Each public method is one database transaction. */
@Service
public class OrderService {

    private final CatalogService catalogService;
    private final OrderRepository orders;

    OrderService(CatalogService catalogService, OrderRepository orders) {
        this.catalogService = catalogService;
        this.orders = orders;
    }

    /**
     * Places a one-line order for a catalog configuration.
     *
     * <p>One transaction covers the catalog read and both inserts (customer_order,
     * then order_line). Hibernate runs the inserts when the transaction commits;
     * if either fails, PostgreSQL rolls back both, so an order is never stored
     * without its line.
     */
    @Transactional
    public OrderResponse placeOrder(UUID customerId, UUID configurationId, int quantity) {
        CatalogSnapshot item = catalogService.findOrderable(configurationId)
                .orElseThrow(() -> new ConfigurationNotOrderableException(configurationId));

        CustomerOrder order = orders.save(CustomerOrder.place(customerId, item, quantity, Timestamps.now()));
        return OrderResponse.from(order);
    }

    /**
     * Confirms an order as commercially eligible, for any confirmation source.
     * Checkpoint 6 calls it after a verified test-mode payment; the demo
     * shortcut uses {@link #confirmDemoPayment}. Confirmation never creates a
     * job or starts a printer.
     */
    @Transactional
    public OrderResponse confirm(UUID orderId, ConfirmationSource source) {
        CustomerOrder order = findOrder(orderId);
        order.confirm(source, Timestamps.now());
        return OrderResponse.from(order);
    }

    /** Demo-only payment for the customer's own order; another customer's order is not found. */
    @Transactional
    public OrderResponse confirmDemoPayment(UUID customerId, UUID orderId) {
        CustomerOrder order = findOwnedOrder(customerId, orderId);
        order.confirm(ConfirmationSource.DEMO_SIMULATED, Timestamps.now());
        return OrderResponse.from(order);
    }

    /** For production release: the order must be CONFIRMED. */
    @Transactional(readOnly = true)
    public ReleasableOrder requireReleasable(UUID orderId) {
        CustomerOrder order = findOrder(orderId);
        order.requireConfirmed();
        return new ReleasableOrder(order.getId(), order.getLine().getRecipeRevisionId());
    }

    /** Called by production only after the order's job has passed QC. */
    @Transactional
    public OrderResponse markReadyForHandoff(UUID orderId) {
        CustomerOrder order = findOrder(orderId);
        order.markReadyForHandoff(Timestamps.now());
        return OrderResponse.from(order);
    }

    /** Any order, for operators and other modules. Customers use {@link #getOwnOrder}. */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return OrderResponse.from(findOrder(orderId));
    }

    /** The customer's own order; another customer's order is "not found", not "forbidden". */
    @Transactional(readOnly = true)
    public OrderResponse getOwnOrder(UUID customerId, UUID orderId) {
        return OrderResponse.from(findOwnedOrder(customerId, orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOwnOrders(UUID customerId, int page, int size) {
        return PageResponse.of(orders.findByCustomerIdOrderByCreatedAtDesc(customerId,
                PageRequest.of(PageResponse.page(page), PageResponse.size(size))), OrderResponse::from);
    }

    /** Orders in the given states, oldest first, for the operator queue. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listByStatus(Collection<OrderStatus> statuses, int page, int size) {
        return PageResponse.of(orders.findByStatusInOrderByCreatedAtAsc(statuses,
                PageRequest.of(PageResponse.page(page), PageResponse.size(size))), OrderResponse::from);
    }

    private CustomerOrder findOwnedOrder(UUID customerId, UUID orderId) {
        return orders.findOwnedWithLine(orderId, customerId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private CustomerOrder findOrder(UUID orderId) {
        return orders.findWithLine(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
