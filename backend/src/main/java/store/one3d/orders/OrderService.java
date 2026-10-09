package store.one3d.orders;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.catalog.CatalogService;
import store.one3d.catalog.CatalogSnapshot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        // PostgreSQL stores microseconds; truncating keeps the returned value
        // identical to what a later read returns.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        CustomerOrder order = orders.save(CustomerOrder.place(item, quantity, now));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return orders.findWithLine(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
