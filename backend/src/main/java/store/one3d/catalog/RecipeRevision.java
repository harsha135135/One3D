package store.one3d.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * One approved, append-only revision of a configuration's manufacturing
 * instructions. A change creates a new revision instead of editing this one.
 * Only the fields Checkpoint 1 reads are mapped; the instructions themselves
 * (material, layer height, notes) stay in the table until production uses them.
 */
@Entity
@Table(name = "recipe_revision")
@Immutable
class RecipeRevision {

    @Id
    private UUID id;

    @Column(name = "revision_number")
    private int revisionNumber;

    protected RecipeRevision() {
        // for JPA
    }

    UUID getId() {
        return id;
    }

    int getRevisionNumber() {
        return revisionNumber;
    }
}
