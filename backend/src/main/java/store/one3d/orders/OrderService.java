package store.one3d.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import store.one3d.catalog.CatalogService;
import store.one3d.catalog.CatalogSnapshot;
import store.one3d.common.PageResponse;
import store.one3d.common.Timestamps;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Order use cases. Each public method is one database transaction. */
@Service
public class OrderService {

    private static final String DEMO_CONFIRMATION = "Confirmed by the demo shortcut; no payment was taken";

    private final CatalogService catalogService;
    private final OrderRepository orders;
    private final OrderFacts facts;
    private final Duration catalogPaymentWindow;

    OrderService(CatalogService catalogService, OrderRepository orders, OrderFacts facts,
                 @Value("${one3d.orders.catalog-payment-window}") Duration catalogPaymentWindow) {
        this.catalogService = catalogService;
        this.orders = orders;
        this.facts = facts;
        this.catalogPaymentWindow = catalogPaymentWindow;
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

        Instant now = Timestamps.now();
        CustomerOrder order = orders.saveAndFlush(
                CustomerOrder.place(customerId, item, quantity, now, now.plus(catalogPaymentWindow)));
        facts.record(order.getId(), OrderFactKind.PLACED, now, null);
        return OrderResponse.from(order);
    }

    /**
     * Places the order for an accepted quote. Called by the quotes module inside
     * its acceptance transaction, so the order and the accepted quote are
     * committed together or not at all.
     */
    @Transactional
    public OrderResponse placeFromQuote(UUID customerId, QuotedItem item) {
        Instant now = Timestamps.now();
        CustomerOrder order = orders.saveAndFlush(CustomerOrder.placeFromQuote(customerId, item, now));
        facts.record(order.getId(), OrderFactKind.PLACED, now, "From quote " + item.quoteId().toString().substring(0, 8));
        return OrderResponse.from(order);
    }

    /**
     * Confirms an order as commercially eligible, for any confirmation source.
     * Checkpoint 6 calls it after a verified test-mode payment; the demo
     * shortcut uses {@link #confirmDemoPayment}. Confirmation never creates a
     * job or starts a printer.
     */
    @Transactional
    public OrderResponse confirm(UUID orderId, ConfirmationSource source, String detail) {
        CustomerOrder order = findOrder(orderId);
        Instant now = Timestamps.now();
        order.confirm(source, now);
        facts.record(orderId, OrderFactKind.CONFIRMED, now, detail);
        return OrderResponse.from(order);
    }

    /** Demo-only payment for the customer's own order; another customer's order is not found. */
    @Transactional
    public OrderResponse confirmDemoPayment(UUID customerId, UUID orderId) {
        CustomerOrder order = findOwnedOrder(customerId, orderId);
        Instant now = Timestamps.now();
        order.confirm(ConfirmationSource.DEMO_SIMULATED, now);
        facts.record(orderId, OrderFactKind.CONFIRMED, now, DEMO_CONFIRMATION);
        return OrderResponse.from(order);
    }

    // --- for the payments module (each joins the caller's transaction) -------

    /**
     * For opening a checkout: locks the customer's own order, which must await
     * payment within its payment window. Anyone else's order is "not found".
     */
    @Transactional
    public PayableOrder requirePayable(UUID customerId, UUID orderId) {
        CustomerOrder order = lock(orderId);
        if (!order.getCustomerId().equals(customerId)) {
            throw new OrderNotFoundException(orderId);
        }
        order.requirePayable(Timestamps.now());
        return PayableOrder.from(order);
    }

    /**
     * Locks the order before a payment for it is decided. Callers must not have
     * loaded the order earlier in the same transaction, or they would decide on
     * the copy read before the lock.
     */
    @Transactional
    public PayableOrder lockForPayment(UUID orderId) {
        return PayableOrder.from(lock(orderId));
    }

    /** After {@link #lockForPayment}: a verified, captured test-mode payment confirms the order. */
    @Transactional
    public void confirmPaid(UUID orderId, String detail) {
        confirm(orderId, ConfirmationSource.RAZORPAY_TEST, detail);
    }

    /** Adds a fact to the order's timeline, in the caller's transaction. */
    @Transactional
    public void recordFact(UUID orderId, OrderFactKind kind, String detail) {
        facts.record(orderId, kind, Timestamps.now(), detail);
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
        Instant now = Timestamps.now();
        order.markReadyForHandoff(now);
        facts.record(orderId, OrderFactKind.READY_FOR_HANDOFF, now, null);
        return OrderResponse.from(order);
    }

    /** An operator records that the customer received the part. */
    @Transactional
    public OrderResponse markHandedOver(UUID orderId) {
        CustomerOrder order = findOrder(orderId);
        Instant now = Timestamps.now();
        order.markHandedOver(now);
        facts.record(orderId, OrderFactKind.HANDED_OVER, now, null);
        return OrderResponse.from(order);
    }

    /** The customer's own order's timeline, oldest first. */
    @Transactional(readOnly = true)
    public List<TimelineEntry> ownTimeline(UUID customerId, UUID orderId) {
        findOwnedOrder(customerId, orderId);
        return facts.of(orderId);
    }

    /** Any order's timeline, for operators. */
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID orderId) {
        findOrder(orderId);
        return facts.of(orderId);
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

    private CustomerOrder lock(UUID orderId) {
        return orders.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private CustomerOrder findOwnedOrder(UUID customerId, UUID orderId) {
        return orders.findOwnedWithLine(orderId, customerId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private CustomerOrder findOrder(UUID orderId) {
        return orders.findWithLine(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
