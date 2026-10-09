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
    static CustomerOrder place(CatalogSnapshot item, int quantity, Instant createdAt) {
        if (quantity != V1_QUANTITY) {
            throw new IllegalArgumentException("A V1 order has quantity " + V1_QUANTITY + ", not " + quantity);
        }
        CustomerOrder order = new CustomerOrder();
        order.status = OrderStatus.AWAITING_PAYMENT;
        order.createdAt = createdAt;
        order.line = new OrderLine(order, item, quantity);
        return order;
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

    OrderLine getLine() {
        return line;
    }
}
