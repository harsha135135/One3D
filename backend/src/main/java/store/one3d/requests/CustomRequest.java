package store.one3d.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import store.one3d.common.ConflictException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A customer's request to print their own model. Its content lives in
 * immutable {@link SubmissionRevision}s; this row only tracks the review state.
 */
@Entity
@Table(name = "custom_request")
class CustomRequest {

    private static final Set<RequestStatus> OPEN_FOR_REVISION =
            Set.of(RequestStatus.SUBMITTED, RequestStatus.CHANGES_REQUIRED, RequestStatus.QUOTED);
    private static final Set<RequestStatus> QUOTABLE = Set.of(RequestStatus.SUBMITTED, RequestStatus.QUOTED);

    @Id
    private UUID id;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Column(name = "operator_message")
    private String operatorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Null until first saved, so Spring Data knows the row is new (no SELECT before INSERT).
    @Version
    private Long version;

    protected CustomRequest() {
        // for JPA
    }

    static CustomRequest submit(UUID customerId, Instant now) {
        CustomRequest request = new CustomRequest();
        request.id = UUID.randomUUID();
        request.customerId = customerId;
        request.status = RequestStatus.SUBMITTED;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    /**
     * A new revision replaces what is being reviewed. Nothing carries over: an
     * open quote is withdrawn by the quotes module and the request is reviewed again.
     */
    void acceptNewRevision(Instant now) {
        if (!OPEN_FOR_REVISION.contains(status)) {
            throw new ConflictException("REQUEST_CLOSED", "Request " + id + " is " + status + "; it takes no new revisions.");
        }
        status = RequestStatus.SUBMITTED;
        operatorMessage = null;
        updatedAt = now;
    }

    void requestChanges(String message, Instant now) {
        require(Set.of(RequestStatus.SUBMITTED), "REQUEST_NOT_IN_REVIEW", "request changes on");
        status = RequestStatus.CHANGES_REQUIRED;
        operatorMessage = message;
        updatedAt = now;
    }

    void reject(String reason, Instant now) {
        require(Set.of(RequestStatus.SUBMITTED, RequestStatus.CHANGES_REQUIRED), "REQUEST_NOT_IN_REVIEW", "reject");
        status = RequestStatus.REJECTED;
        operatorMessage = reason;
        updatedAt = now;
    }

    void requireQuotable() {
        require(QUOTABLE, "REQUEST_NOT_QUOTABLE", "quote");
    }

    void markQuoted(Instant now) {
        requireQuotable();
        status = RequestStatus.QUOTED;
        updatedAt = now;
    }

    void markOrdered(Instant now) {
        require(Set.of(RequestStatus.QUOTED), "REQUEST_NOT_QUOTED", "order from");
        status = RequestStatus.ORDERED;
        updatedAt = now;
    }

    private void require(Set<RequestStatus> allowed, String code, String action) {
        if (!allowed.contains(status)) {
            throw new ConflictException(code, "Cannot " + action + " request " + id + " while it is " + status + ".");
        }
    }

    UUID getId() {
        return id;
    }

    UUID getCustomerId() {
        return customerId;
    }

    RequestStatus getStatus() {
        return status;
    }

    String getOperatorMessage() {
        return operatorMessage;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
