package store.one3d.production;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.SimulatorRig;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The printing and work-queue metrics follow what happens, and no metric label
 * carries an id (labels are fixed names, so the number of series stays small).
 */
@PostgresIntegrationTest
class ProductionMetricsIT {

    private static final Pattern UUID_SHAPE =
            Pattern.compile(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    SimulatorRig simulator;

    @Autowired
    MeterRegistry registry;

    private ApiSteps api;
    private String printerId;
    private ListAppender<ILoggingEvent> dispatchLog;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc, worker, observer, simulator);
        printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
        dispatchLog = new ListAppender<>();
        dispatchLog.start();
        ((Logger) LoggerFactory.getLogger(DispatchWorker.class)).addAppender(dispatchLog);
    }

    @AfterEach
    void tearDown() {
        simulator.setUnreachable(false);
        ((Logger) LoggerFactory.getLogger(DispatchWorker.class)).detachAppender(dispatchLog);
    }

    @Test
    void anUnreachableSimulatorShowsInTheMetricsAndTheLogsUntilTheStartIsReconciled() throws Exception {
        String jobId = api.releasedJob();
        String attemptId = api.recordStart(jobId, printerId);
        assertThat(gauge("one3d.work.items", "kind", "DISPATCH_START", "status", "PENDING")).isGreaterThanOrEqualTo(1);
        assertThat(gauge("one3d.work.oldest.age", "kind", "DISPATCH_START")).isGreaterThanOrEqualTo(0);
        double unknown = counter("one3d.dispatch.results", "result", "unknown");
        double unreachable = counter("one3d.simulator.unreachable", "operation", "dispatch");
        double awaiting = gauge("one3d.attempts.awaiting.reconciliation");

        simulator.setUnreachable(true);
        api.dispatch();

        // The worker may also pick up work other tests left queued, so the counts rise by at least one.
        assertThat(counter("one3d.dispatch.results", "result", "unknown")).isGreaterThan(unknown);
        assertThat(counter("one3d.simulator.unreachable", "operation", "dispatch")).isGreaterThan(unreachable);
        double uncertain = gauge("one3d.attempts.awaiting.reconciliation");
        assertThat(uncertain).isGreaterThan(awaiting);
        // The warning about this attempt names its command, attempt and job in the log context.
        assertThat(dispatchLog.list)
                .filteredOn(event -> attemptId.equals(event.getMDCPropertyMap().get("attempt_id")))
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.getFormattedMessage()).contains("not confirmed");
                    assertThat(warning.getMDCPropertyMap()).containsEntry("job_id", jobId).containsKey("command_id");
                });

        simulator.setUnreachable(false);
        double started = counter("one3d.dispatch.results", "result", "started");
        double completed = counter("one3d.attempts.ended", "result", "completed");
        api.dispatch();
        assertThat(counter("one3d.dispatch.results", "result", "started")).isGreaterThan(started);
        assertThat(gauge("one3d.attempts.awaiting.reconciliation")).isLessThan(uncertain);

        api.complete(attemptId);
        assertThat(counter("one3d.attempts.ended", "result", "completed")).isGreaterThan(completed);
    }

    @Test
    void noMetricLabelCarriesAnId() throws Exception {
        String orderId = api.placeConfirmedOrder();
        api.get("/api/orders/" + orderId);
        api.get("/api/orders/" + orderId + "/timeline");
        api.get("/api/ops/orders/" + orderId);
        api.get("/api/files/" + UUID.randomUUID());

        List<String> withIds = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream()
                        .filter(tag -> UUID_SHAPE.matcher(tag.getValue()).matches())
                        .map(tag -> meter.getId().getName() + " " + tag))
                .toList();
        assertThat(withIds).isEmpty();
        // Routes are recorded by their template, for example /api/orders/{orderId}.
        assertThat(registry.find("http.server.requests").meters().stream()
                .map(meter -> meter.getId().getTag("uri")))
                .contains("/api/orders/{orderId}", "/api/orders/{orderId}/timeline");
    }

    private double counter(String name, String... tags) {
        return registry.find(name).tags(tags).counter().count();
    }

    private double gauge(String name, String... tags) {
        return registry.find(name).tags(tags).gauge().value();
    }
}
