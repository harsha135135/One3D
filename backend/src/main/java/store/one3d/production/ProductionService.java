package store.one3d.production;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;
import store.one3d.common.ConflictException;
import store.one3d.common.NotFoundException;
import store.one3d.common.Timestamps;
import store.one3d.orders.OrderFactKind;
import store.one3d.orders.OrderResponse;
import store.one3d.orders.OrderService;
import store.one3d.orders.ReleasableOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Operator production use cases. Each public method is one transaction. The
 * rules live in ProductionJob, PrintAttempt and Printer; this class loads the
 * records, calls them and returns the result.
 *
 * <p>Nothing here runs automatically: every step is an explicit operator call,
 * and placing or paying for an order never creates a job or starts a printer.
 * Talking to the simulator happens later, in {@link DispatchWorker}, outside
 * these transactions.
 */
@Service
public class ProductionService {

    private final OrderService orderService;
    private final ProductionJobRepository jobs;
    private final PrintAttemptRepository attempts;
    private final PrinterRepository printers;
    private final BedClearanceRepository clearances;
    private final PrinterCommandRepository commands;
    private final PendingWorkStore work;
    private final int maxDispatchTries;

    ProductionService(OrderService orderService, ProductionJobRepository jobs, PrintAttemptRepository attempts,
                      PrinterRepository printers, BedClearanceRepository clearances,
                      PrinterCommandRepository commands, PendingWorkStore work,
                      @Value("${one3d.worker.max-attempts}") int maxDispatchTries) {
        this.orderService = orderService;
        this.jobs = jobs;
        this.attempts = attempts;
        this.printers = printers;
        this.clearances = clearances;
        this.commands = commands;
        this.work = work;
        this.maxDispatchTries = maxDispatchTries;
    }

    /**
     * Creates the order's job. The order must be CONFIRMED and not yet released.
     * Two simultaneous releases are stopped by the unique constraint on the
     * order id; the loser gets 409 ALREADY_RELEASED.
     */
    @Transactional
    public JobResponse release(UUID orderId) {
        ReleasableOrder order = orderService.requireReleasable(orderId);
        if (jobs.existsByOrderId(orderId)) {
            throw new ConflictException("ALREADY_RELEASED", "Order " + orderId + " already has a production job.");
        }
        ProductionJob job = jobs.saveAndFlush(
                ProductionJob.release(order.orderId(), order.recipeRevisionId(), Timestamps.now()));
        orderService.recordFact(orderId, OrderFactKind.RELEASED_TO_PRODUCTION, null);
        return JobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId) {
        return JobResponse.from(findJob(jobId));
    }

    /**
     * Records the intent to start the job's next attempt on a printer, in one
     * transaction:
     * <ol>
     *   <li>lock the printer row, so a competing start waits and then sees it busy;</li>
     *   <li>create the attempt (START_INTENT) and reserve the printer and bed;</li>
     *   <li>consume the operator's clearance of the previous print, if any;</li>
     *   <li>store the start command and the work row that will deliver it.</li>
     * </ol>
     * The simulator is contacted afterwards by the dispatch worker.
     */
    @Transactional
    public JobResponse startAttempt(UUID jobId, UUID printerId) {
        ProductionJob job = findJob(jobId);
        Printer printer = printers.findForUpdate(printerId).orElseThrow(() -> printerNotFound(printerId));
        Optional<BedClearance> clearance = clearances.findByPrinterIdAndConsumedByAttemptIdIsNull(printerId);
        Instant now = Timestamps.now();

        PrintAttempt attempt = attempts.save(job.startAttempt(printer, now));
        clearance.ifPresent(c -> c.consumeFor(attempt.getId()));
        UUID clearedAttemptId = clearance.map(BedClearance::getClearedAttemptId).orElse(null);
        PrinterCommand command = commands.saveAndFlush(
                PrinterCommand.start(attempt, job.getRecipeRevisionId(), clearedAttemptId, now));
        work.enqueue(WorkKind.DISPATCH_START, command.getId(), maxDispatchTries);
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse recordQc(UUID attemptId, QcResult result, String notes) {
        PrintAttempt attempt = findAttempt(attemptId);
        ProductionJob job = attempt.getJob();
        job.recordQc(attempt, result, notes, Timestamps.now());
        if (result == QcResult.PASSED) {
            orderService.recordFact(job.getOrderId(), OrderFactKind.QC_PASSED, null);
        }
        return JobResponse.from(job);
    }

    /**
     * Asks again for an attempt whose start could not be confirmed and whose
     * automatic retries ran out. It does not start anything new: the same
     * command is checked against the simulator's journal.
     */
    @Transactional
    public JobResponse retryReconciliation(UUID attemptId) {
        PrintAttempt attempt = findAttempt(attemptId);
        PrinterCommand command = commands.findByAttemptId(attemptId).orElseThrow(() -> new ConflictException(
                "NOTHING_TO_RECONCILE", "Attempt " + attemptId + " has no start command."));
        if (work.requeueExhausted(WorkKind.DISPATCH_START, command.getId()) == 0) {
            throw new ConflictException("NOTHING_TO_RECONCILE",
                    "Attempt " + attemptId + " has no exhausted delivery to retry.");
        }
        return JobResponse.from(attempt.getJob());
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

    /** Handoff is an order step; it is here beside readiness so operators find both in one place. */
    @Transactional
    public OrderResponse markHandedOver(UUID orderId) {
        return orderService.markHandedOver(orderId);
    }

    @Transactional(readOnly = true)
    public List<PrinterResponse> listPrinters() {
        return printers.findAllByOrderByName().stream().map(PrinterResponse::from).toList();
    }

    /**
     * An operator confirms that the print of {@code clearedAttemptId} was
     * removed. It must be the print on the bed now; see {@link Printer#clearBed}.
     */
    @Transactional
    public PrinterResponse clearBed(UUID printerId, UUID clearedAttemptId) {
        Printer printer = printers.findForUpdate(printerId).orElseThrow(() -> printerNotFound(printerId));
        Instant now = Timestamps.now();
        printer.clearBed(clearedAttemptId, now);
        clearances.save(new BedClearance(printerId, clearedAttemptId, now));
        return PrinterResponse.from(printer);
    }

    /** Background work in a given state, for example EXHAUSTED work that needs an operator. */
    @Transactional(readOnly = true)
    public List<PendingWorkStore.WorkView> listWork(String status) {
        return work.list(status);
    }

    private ProductionJob findJob(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new NotFoundException("JOB_NOT_FOUND", "Job " + jobId + " was not found."));
    }

    private PrintAttempt findAttempt(UUID attemptId) {
        return attempts.findWithJob(attemptId)
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Attempt " + attemptId + " was not found."));
    }

    private static NotFoundException printerNotFound(UUID printerId) {
        return new NotFoundException("PRINTER_NOT_FOUND", "Printer " + printerId + " was not found.");
    }
}
