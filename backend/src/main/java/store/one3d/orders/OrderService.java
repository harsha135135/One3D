package store.one3d.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.catalog.CatalogService;
import store.one3d.catalog.CatalogSnapshot;
import store.one3d.common.Timestamps;

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
    public OrderResponse placeOrder(UUID configurationId, int quantity) {
        CatalogSnapshot item = catalogService.findOrderable(configurationId)
                .orElseThrow(() -> new ConfigurationNotOrderableException(configurationId));

        CustomerOrder order = orders.save(CustomerOrder.place(item, quantity, Timestamps.now()));
        return OrderResponse.from(order);
    }

    /**
     * Confirms an order as commercially eligible. In V1 the only caller is the
     * demo-only {@link DemoPaymentController}; Checkpoint 6 adds verified
     * test-mode payments. Confirmation never creates a job or starts a printer.
     */
    @Transactional
    public OrderResponse confirm(UUID orderId, ConfirmationSource source) {
        CustomerOrder order = findOrder(orderId);
        order.confirm(source, Timestamps.now());
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

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return OrderResponse.from(findOrder(orderId));
    }

    private CustomerOrder findOrder(UUID orderId) {
        return orders.findWithLine(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
