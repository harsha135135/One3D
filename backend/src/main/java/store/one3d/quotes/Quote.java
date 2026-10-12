package store.one3d.quotes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import store.one3d.common.ConflictException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A price for one exact submission revision, valid until {@code expiresAt}.
 * Its recipe revision (owned by the catalog module) says how it will be made.
 * Accepting it creates exactly one order.
 */
@Entity
@Table(name = "quote")
class Quote {

    @Id
    private UUID id;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "revision_id", updatable = false)
    private UUID revisionId;

    @Column(name = "price_minor", updatable = false)
    private long priceMinor;

    @Column(updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    private QuoteStatus status;

    @Column(name = "issued_at", updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "order_id")
    private UUID orderId;

    // Null until first saved, so Spring Data knows the row is new.
    @Version
    private Long version;

    protected Quote() {
        // for JPA
    }

    static Quote issue(UUID requestId, UUID revisionId, long priceMinor, String currency, Instant now,
                       Duration validFor) {
        Quote quote = new Quote();
        quote.id = UUID.randomUUID();
        quote.requestId = requestId;
        quote.revisionId = revisionId;
        quote.priceMinor = priceMinor;
        quote.currency = currency;
        quote.status = QuoteStatus.ISSUED;
        quote.issuedAt = now;
        quote.expiresAt = now.plus(validFor);
        return quote;
    }

    /** True from the expiry instant on. */
    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void withdraw(Instant now) {
        if (status != QuoteStatus.ISSUED) {
            throw new IllegalStateException("Quote " + id + " is " + status + ", not ISSUED");
        }
        status = QuoteStatus.WITHDRAWN;
        withdrawnAt = now;
    }

    /** Checks that the quote can be accepted now. An ACCEPTED quote is handled by the caller (same order again). */
    void requireAcceptable(Instant now) {
        if (status == QuoteStatus.WITHDRAWN) {
            throw new ConflictException("QUOTE_WITHDRAWN", "Quote " + id + " was withdrawn and cannot be accepted.");
        }
        if (isExpired(now)) {
            throw new ConflictException("QUOTE_EXPIRED", "Quote " + id + " expired at " + expiresAt + ".");
        }
    }

    void accept(UUID createdOrderId, Instant now) {
        requireAcceptable(now);
        if (status != QuoteStatus.ISSUED) {
            throw new IllegalStateException("Quote " + id + " is " + status + ", not ISSUED");
        }
        status = QuoteStatus.ACCEPTED;
        orderId = createdOrderId;
        acceptedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getRequestId() {
        return requestId;
    }

    UUID getRevisionId() {
        return revisionId;
    }

    long getPriceMinor() {
        return priceMinor;
    }

    String getCurrency() {
        return currency;
    }

    QuoteStatus getStatus() {
        return status;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }

    Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    UUID getOrderId() {
        return orderId;
    }
}
