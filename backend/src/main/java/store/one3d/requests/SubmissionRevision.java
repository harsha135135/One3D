package store.one3d.requests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One model file plus the customer's requirements, exactly as submitted.
 * Never changed ({@code @Immutable}: Hibernate issues no UPDATE): a different
 * file or different requirements are a new revision, reviewed from scratch.
 */
@Entity
@Table(name = "submission_revision")
@Immutable
class SubmissionRevision {

    @Id
    private UUID id;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "revision_number")
    private int revisionNumber;

    @Column(name = "artifact_id")
    private UUID artifactId;

    private String material;

    private String colour;

    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SubmissionRevision() {
        // for JPA
    }

    SubmissionRevision(UUID requestId, int revisionNumber, Requirements requirements, Instant now) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.revisionNumber = revisionNumber;
        this.artifactId = requirements.artifactId();
        this.material = requirements.material();
        this.colour = requirements.colour();
        this.notes = requirements.notes() == null ? "" : requirements.notes();
        this.createdAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getRequestId() {
        return requestId;
    }

    int getRevisionNumber() {
        return revisionNumber;
    }

    UUID getArtifactId() {
        return artifactId;
    }

    String getMaterial() {
        return material;
    }

    String getColour() {
        return colour;
    }

    String getNotes() {
        return notes;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
