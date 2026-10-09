package store.one3d.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read access to the catalog for the API and for other modules. */
@Service
public class CatalogService {

    private final ProductConfigurationRepository configurations;

    CatalogService(ProductConfigurationRepository configurations) {
        this.configurations = configurations;
    }

    /**
     * Returns the current catalog values for a configuration that can be
     * ordered, or empty if it does not exist or has no approved recipe.
     * When called from OrderService, this runs inside the order transaction.
     */
    @Transactional(readOnly = true)
    public Optional<CatalogSnapshot> findOrderable(UUID configurationId) {
        return configurations.findOrderable(configurationId).map(ProductConfiguration::toSnapshot);
    }

    @Transactional(readOnly = true)
    public List<CatalogSnapshot> listOrderable() {
        return configurations.findAllOrderable().stream().map(ProductConfiguration::toSnapshot).toList();
    }
}
