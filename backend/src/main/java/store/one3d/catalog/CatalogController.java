package store.one3d.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Lists orderable configurations. V1 has a single seeded item, so no pagination yet. */
    @GetMapping("/configurations")
    List<CatalogItemResponse> listConfigurations() {
        return catalogService.listOrderable().stream().map(CatalogItemResponse::from).toList();
    }
}
