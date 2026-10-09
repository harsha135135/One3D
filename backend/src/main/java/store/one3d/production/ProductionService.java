package store.one3d.production;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.common.ConflictException;
import store.one3d.common.NotFoundException;
import store.one3d.common.Timestamps;
import store.one3d.orders.OrderResponse;
import store.one3d.orders.OrderService;
import store.one3d.orders.ReleasableOrder;

import java.util.List;
import java.util.UUID;

/**
 * Operator production use cases. Each public method is one transaction. The
 * rules live in ProductionJob, PrintAttempt and Printer; this class loads the
 * records, calls them and returns the result.
 *
 * <p>Nothing here runs automatically: every step is an explicit operator call,
 * and placing or paying for an order never creates a job or starts a printer.
 * Concurrent operator calls are not yet protected (Checkpoint 3).
 */
@Service
public class ProductionService {

    private final OrderService orderService;
    private final ProductionJobRepository jobs;
    private final PrintAttemptRepository attempts;
    private final PrinterRepository printers;

    ProductionService(OrderService orderService, ProductionJobRepository jobs,
                      PrintAttemptRepository attempts, PrinterRepository printers) {
        this.orderService = orderService;
        this.jobs = jobs;
        this.attempts = attempts;
        this.printers = printers;
    }

    /** Creates the order's job. The order must be CONFIRMED and not yet released. */
    @Transactional
    public JobResponse release(UUID orderId) {
        ReleasableOrder order = orderService.requireReleasable(orderId);
        if (jobs.existsByOrderId(orderId)) {
            throw new ConflictException("ALREADY_RELEASED", "Order " + orderId + " already has a production job.");
        }
        ProductionJob job = jobs.save(ProductionJob.release(order.orderId(), order.recipeRevisionId(), Timestamps.now()));
        return JobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId) {
        return JobResponse.from(findJob(jobId));
    }

    /** Starts the job's next attempt on the given printer. */
    @Transactional
    public JobResponse startAttempt(UUID jobId, UUID printerId) {
        ProductionJob job = findJob(jobId);
        job.startAttempt(findPrinter(printerId), Timestamps.now());
        return JobResponse.from(job);
    }

    /** Stands in for the printer reporting a finished print (Checkpoint 3 adds a separate simulator). */
    @Transactional
    public JobResponse simulateCompletion(UUID attemptId) {
        PrintAttempt attempt = findAttempt(attemptId);
        ProductionJob job = attempt.getJob();
        job.completeAttempt(attempt, findPrinter(attempt.getPrinterId()), Timestamps.now());
        return JobResponse.from(job);
    }

    /** Stands in for the printer reporting a failed or aborted print. */
    @Transactional
    public JobResponse simulateFailure(UUID attemptId, String reason) {
        PrintAttempt attempt = findAttempt(attemptId);
        ProductionJob job = attempt.getJob();
        job.failAttempt(attempt, findPrinter(attempt.getPrinterId()), reason, Timestamps.now());
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse recordQc(UUID attemptId, QcResult result, String notes) {
        PrintAttempt attempt = findAttempt(attemptId);
        ProductionJob job = attempt.getJob();
        job.recordQc(attempt, result, notes, Timestamps.now());
        return JobResponse.from(job);
    }

    /** Marks the order ready for handoff, but only if its job has an accepted part. */
    @Transactional
    public OrderResponse markReadyForHandoff(UUID orderId) {
        orderService.getOrder(orderId); // 404 if the order does not exist
        ProductionJob job = jobs.findByOrderId(orderId).orElseThrow(() -> new ConflictException(
                "JOB_NOT_ACCEPTED", "Order " + orderId + " has not been released for production."));
        job.requireAccepted();
        return orderService.markReadyForHandoff(orderId);
    }

    @Transactional(readOnly = true)
    public List<PrinterResponse> listPrinters() {
        return printers.findAllByOrderByName().stream().map(PrinterResponse::from).toList();
    }

    @Transactional
    public PrinterResponse clearBed(UUID printerId) {
        Printer printer = findPrinter(printerId);
        printer.clearBed(Timestamps.now());
        return PrinterResponse.from(printer);
    }

    private ProductionJob findJob(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new NotFoundException("JOB_NOT_FOUND", "Job " + jobId + " was not found."));
    }

    private PrintAttempt findAttempt(UUID attemptId) {
        return attempts.findWithJob(attemptId)
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Attempt " + attemptId + " was not found."));
    }

    private Printer findPrinter(UUID printerId) {
        return printers.findById(printerId)
                .orElseThrow(() -> new NotFoundException("PRINTER_NOT_FOUND", "Printer " + printerId + " was not found."));
    }
}
