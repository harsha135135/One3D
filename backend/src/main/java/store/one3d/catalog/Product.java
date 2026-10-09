package store.one3d.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** A catalog product. Read-only in V1: there is no catalog editor. */
@Entity
@Table(name = "product")
@Immutable
class Product {

    @Id
    private UUID id;

    private String name;

    protected Product() {
        // for JPA
    }

    String getName() {
        return name;
    }
}
