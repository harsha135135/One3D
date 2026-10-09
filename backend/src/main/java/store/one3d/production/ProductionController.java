package store.one3d.production;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import store.one3d.orders.OrderResponse;

import java.net.URI;
import java.util.UUID;

/**
 * Operator production actions. "/ops" is only a grouping: there is no
 * authentication until Checkpoint 4, so the API is reachable on 127.0.0.1 only.
 */
@RestController
@RequestMapping("/api/ops")
class ProductionController {

    private final ProductionService productionService;

    ProductionController(ProductionService productionService) {
        this.productionService = productionService;
    }

    @PostMapping("/orders/{orderId}/release")
    ResponseEntity<JobResponse> release(@PathVariable UUID orderId) {
        JobResponse job = productionService.release(orderId);
        return ResponseEntity.created(jobLocation(job.id())).body(job);
    }

    @PostMapping("/orders/{orderId}/ready-for-handoff")
    OrderResponse markReadyForHandoff(@PathVariable UUID orderId) {
        return productionService.markReadyForHandoff(orderId);
    }

    @GetMapping("/jobs/{jobId}")
    JobResponse getJob(@PathVariable UUID jobId) {
        return productionService.getJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/attempts")
    ResponseEntity<JobResponse> startAttempt(@PathVariable UUID jobId, @Valid @RequestBody AssignPrinterRequest request) {
        JobResponse job = productionService.startAttempt(jobId, request.printerId());
        return ResponseEntity.created(jobLocation(jobId)).body(job);
    }

    @PostMapping("/attempts/{attemptId}/simulate-completion")
    JobResponse simulateCompletion(@PathVariable UUID attemptId) {
        return productionService.simulateCompletion(attemptId);
    }

    @PostMapping("/attempts/{attemptId}/simulate-failure")
    JobResponse simulateFailure(@PathVariable UUID attemptId, @Valid @RequestBody SimulatedFailureRequest request) {
        return productionService.simulateFailure(attemptId, request.reason());
    }

    @PostMapping("/attempts/{attemptId}/qc")
    JobResponse recordQc(@PathVariable UUID attemptId, @Valid @RequestBody QcRequest request) {
        return productionService.recordQc(attemptId, request.result(), request.notes());
    }

    private static URI jobLocation(UUID jobId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/ops/jobs/{id}")
                .buildAndExpand(jobId)
                .toUri();
    }
}
