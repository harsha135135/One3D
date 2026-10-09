package store.one3d.production;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import store.one3d.support.ApiSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.SimulatorRig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two operators acting at the same moment against real PostgreSQL. Both
 * requests are released together; exactly one may win and the other gets a
 * 409, never a 500 and never a second owner.
 */
@PostgresIntegrationTest
class ConcurrencyIT {

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

    private ApiSteps api;
    private String printerId;

    @BeforeEach
    void setUp() {
        api = new ApiSteps(mvc, worker, observer, simulator);
        printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
    }

    @RepeatedTest(5)
    void twoSimultaneousStartsOnOnePrinterGiveExactlyOneAttempt() throws Exception {
        String firstJob = api.releasedJob();
        String secondJob = api.releasedJob();

        List<MvcResult> results = race(
                () -> startRequest(firstJob),
                () -> startRequest(secondJob));

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        assertThat(results).filteredOn(r -> r.getResponse().getStatus() == 409)
                .extracting(r -> r.getResponse().getContentAsString())
                .allSatisfy(body -> assertThat(body).containsAnyOf("PRINTER_BUSY", "CONCURRENT_MODIFICATION"));
        long active = jdbc.sql("""
                        SELECT count(*) FROM print_attempt
                        WHERE printer_id = ?::uuid AND status IN ('START_INTENT', 'RUNNING', 'RECONCILIATION_REQUIRED')
                        """)
                .param(printerId).query(Long.class).single();
        assertThat(active).isEqualTo(1);
    }

    @RepeatedTest(5)
    void twoSimultaneousReleasesOfOneOrderGiveExactlyOneJob() throws Exception {
        String orderId = api.placeConfirmedOrder();

        List<MvcResult> results = race(
                () -> mvc.perform(post("/api/ops/orders/" + orderId + "/release")).andReturn(),
                () -> mvc.perform(post("/api/ops/orders/" + orderId + "/release")).andReturn());

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        assertThat(results).filteredOn(r -> r.getResponse().getStatus() == 409)
                .extracting(r -> r.getResponse().getContentAsString())
                .allSatisfy(body -> assertThat(body).contains("ALREADY_RELEASED"));
        long jobs = jdbc.sql("SELECT count(*) FROM production_job WHERE order_id = ?::uuid")
                .param(orderId).query(Long.class).single();
        assertThat(jobs).isEqualTo(1);
    }

    private MvcResult startRequest(String jobId) throws Exception {
        return mvc.perform(post("/api/ops/jobs/" + jobId + "/attempts")
                        .contentType("application/json")
                        .content("{\"printerId\": \"" + printerId + "\"}"))
                .andReturn();
    }

    /** Runs both calls on separate threads, released at the same instant. */
    @SafeVarargs
    private static List<MvcResult> race(Callable<MvcResult>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
