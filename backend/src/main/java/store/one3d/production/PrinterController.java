package store.one3d.production;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops/printers")
class PrinterController {

    private final ProductionService productionService;

    PrinterController(ProductionService productionService) {
        this.productionService = productionService;
    }

    /** One simulated printer in V1, so no pagination. */
    @GetMapping
    List<PrinterResponse> listPrinters() {
        return productionService.listPrinters();
    }

    @PostMapping("/{printerId}/clear-bed")
    PrinterResponse clearBed(@PathVariable UUID printerId) {
        return productionService.clearBed(printerId);
    }
}
