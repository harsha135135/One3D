package store.one3d.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import store.one3d.catalog.CatalogSnapshot;
import store.one3d.common.ConflictException;
import store.one3d.common.Money;

import java.time.Instant;
import java.util.UUID;

/** An order with exactly one line. Use {@link #place} to create one. */
@Entity
@Table(name = "customer_order")
class CustomerOrder {

    /** V1 orders always contain exactly one item. */
    static final int V1_QUANTITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // The signed-in customer who placed the order. Set from the session, never from request data.
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_source")
    private ConfirmationSource confirmationSource;

    @Column(name = "ready_for_handoff_at")
    private Instant readyForHandoffAt;

    // Saving the order also inserts its line (cascade), in the same transaction.
    @OneToOne(mappedBy = "order", cascade = CascadeType.PERSIST, optional = false)
    private OrderLine line;

    protected CustomerOrder() {
        // for JPA
    }

    /**
     * Creates a new order awaiting payment, copying the catalog values into
     * its line so later catalog changes cannot alter it.
     */
    static CustomerOrder place(UUID customerId, CatalogSnapshot item, int quantity, Instant createdAt) {
        if (quantity != V1_QUANTITY) {
            throw new IllegalArgumentException("A V1 order has quantity " + V1_QUANTITY + ", not " + quantity);
        }
        CustomerOrder order = new CustomerOrder();
        order.customerId = customerId;
        order.status = OrderStatus.AWAITING_PAYMENT;
        order.createdAt = createdAt;
        order.line = new OrderLine(order, item, quantity);
        return order;
    }

    /** Creates a new order awaiting payment for an accepted quote, copying the quote's values. */
    static CustomerOrder placeFromQuote(UUID customerId, QuotedItem item, Instant createdAt) {
        CustomerOrder order = new CustomerOrder();
        order.customerId = customerId;
        order.status = OrderStatus.AWAITING_PAYMENT;
        order.createdAt = createdAt;
        order.line = new OrderLine(order, item, V1_QUANTITY);
        return order;
    }

    /**
     * Marks the order as commercially eligible for production. This does not
     * release or start anything: an operator releases production separately.
     */
    void confirm(ConfirmationSource source, Instant now) {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw new ConflictException("ORDER_NOT_AWAITING_PAYMENT",
                    "Order " + id + " is " + status + ", not AWAITING_PAYMENT.");
        }
        status = OrderStatus.CONFIRMED;
        confirmedAt = now;
        confirmationSource = source;
    }

    /** Production checks that the job was accepted before calling this. */
    void markReadyForHandoff(Instant now) {
        requireConfirmed();
        status = OrderStatus.READY_FOR_HANDOFF;
        readyForHandoffAt = now;
    }

    void requireConfirmed() {
        if (status != OrderStatus.CONFIRMED) {
            throw new ConflictException("ORDER_NOT_CONFIRMED",
                    "Order " + id + " is " + status + ", not CONFIRMED.");
        }
    }

    /** Price of the single line times its quantity. */
    Money total() {
        return new Money(line.getUnitPriceMinor() * line.getQuantity(), line.getCurrency());
    }

    UUID getId() {
        return id;
    }

    OrderStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    UUID getCustomerId() {
        return customerId;
    }

    Instant getConfirmedAt() {
        return confirmedAt;
    }

    ConfirmationSource getConfirmationSource() {
        return confirmationSource;
    }

    Instant getReadyForHandoffAt() {
        return readyForHandoffAt;
    }

    OrderLine getLine() {
        return line;
    }
}
