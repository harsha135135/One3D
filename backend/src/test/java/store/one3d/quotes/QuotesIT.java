package store.one3d.quotes;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import store.one3d.files.ArtifactVerifier;
import store.one3d.production.DispatchWorker;
import store.one3d.production.ExecutionObserver;
import store.one3d.support.ApiSteps;
import store.one3d.support.FileSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.ProductionFixtures;
import store.one3d.support.RequestSteps;
import store.one3d.support.SimulatorRig;
import store.one3d.support.StlFixtures;
import store.one3d.support.StorageRig;
import store.one3d.support.TestUsers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quotes and their acceptance: a quote prices one exact revision, file version
 * and recipe; acceptance freezes it into exactly one order; withdrawn, expired
 * and outdated quotes cannot be accepted.
 */
@PostgresIntegrationTest
class QuotesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StorageRig storage;

    @Autowired
    ArtifactVerifier verifier;

    @Autowired
    DispatchWorker worker;

    @Autowired
    ExecutionObserver observer;

    @Autowired
    SimulatorRig simulator;

    private FileSteps files;
    private RequestSteps requests;
    private RequestPostProcessor alice;
    private RequestPostProcessor bob;

    @BeforeEach
    void setUp() {
        files = new FileSteps(mvc, storage, verifier);
        requests = new RequestSteps(mvc, files);
        alice = TestUsers.customer("alice-" + UUID.randomUUID());
        bob = TestUsers.customer("bob-" + UUID.randomUUID());
    }

    @Test
    void acceptingAQuoteCreatesAnOrderForTheExactRevisionFileAndRecipe() throws Exception {
        byte[] model = StlFixtures.binary(9);
        FileSteps.Upload upload = files.startUpload(alice, "hinge.stl", model.length);
        files.put(upload, model);
        files.finalizeRequest(alice, upload.artifactId()).andExpect(status().isOk());
        files.verify();
        String requestId = JsonPath.read(mvc.perform(post("/api/requests").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"artifactId": "%s", "material": "PETG", "colour": "Orange", "notes": "Outdoor use"}
                                """.formatted(upload.artifactId())))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String revisionId = requests.latestRevisionId(requestId);

        String quote = requests.quoteRequest(requestId, revisionId, 129_900, 7)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.revisionId").value(revisionId))
                .andExpect(jsonPath("$.price.amountMinor").value(129_900))
                .andExpect(jsonPath("$.price.currency").value("INR"))
                .andExpect(jsonPath("$.acceptable").value(true))
                .andExpect(jsonPath("$.recipe.revisionNumber").value(1))
                .andExpect(jsonPath("$.recipe.layerHeightMicrons").value(200))
                .andReturn().getResponse().getContentAsString();
        String quoteId = JsonPath.read(quote, "$.id");
        String recipeId = JsonPath.read(quote, "$.recipe.id");
        mvc.perform(get("/api/requests/{id}", requestId).with(alice)).andExpect(jsonPath("$.status").value("QUOTED"));

        String order = requests.accept(alice, quoteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.line.source").value("CUSTOM_QUOTE"))
                .andExpect(jsonPath("$.line.quoteId").value(quoteId))
                .andExpect(jsonPath("$.line.productConfigurationId").doesNotExist())
                .andExpect(jsonPath("$.line.recipeRevisionId").value(recipeId))
                .andExpect(jsonPath("$.line.productName").value("Custom part: hinge.stl"))
                .andExpect(jsonPath("$.line.configurationDescription").value("PETG, Orange (revision 1)"))
                .andExpect(jsonPath("$.total.amountMinor").value(129_900))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(order, "$.id");
        String versionAtAcceptance = jdbc.sql("SELECT version_id FROM artifact WHERE id = ?::uuid")
                .param(upload.artifactId()).query(String.class).single();

        // The customer PUTs different bytes to the same key while the upload URL is still valid.
        files.put(upload, StlFixtures.binary(9, "different model"));

        Map<String, Object> scope = acceptedScope(orderId);
        assertThat(scope.get("quote_id").toString()).isEqualTo(quoteId);
        assertThat(scope.get("recipe_revision_id").toString()).isEqualTo(recipeId);
        assertThat(scope.get("revision_id").toString()).isEqualTo(revisionId);
        assertThat(scope.get("material")).isEqualTo("PETG");
        assertThat(scope.get("notes")).isEqualTo("Outdoor use");
        assertThat(scope.get("artifact_id").toString()).isEqualTo(upload.artifactId());
        assertThat(scope.get("version_id")).isEqualTo(versionAtAcceptance);
        assertThat(scope.get("sha256")).isEqualTo(StlFixtures.sha256(model));
        assertThat(scope.get("recipe_quote_id").toString()).isEqualTo(quoteId);
        mvc.perform(get("/api/requests/{id}", requestId).with(alice)).andExpect(jsonPath("$.status").value("ORDERED"));
    }

    @Test
    void acceptingAgainReturnsTheSameOrder() throws Exception {
        String quoteId = requests.quoted(requests.submitted(alice, "twice.stl"), 50_000);

        String first = JsonPath.read(requests.accept(alice, quoteId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(requests.accept(alice, quoteId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(ordersForQuote(quoteId)).isOne();
    }

    @RepeatedTest(5)
    void twoSimultaneousAcceptancesCreateOneOrderAndBothSeeIt() throws Exception {
        String quoteId = requests.quoted(requests.submitted(alice, "race.stl"), 50_000);

        List<MvcResult> results = concurrently(
                () -> requests.accept(alice, quoteId).andReturn(),
                () -> requests.accept(alice, quoteId).andReturn());

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactly(200, 200);
        assertThat(results).extracting(r -> (String) JsonPath.read(r.getResponse().getContentAsString(), "$.id"))
                .containsOnly((String) JsonPath.read(results.get(0).getResponse().getContentAsString(), "$.id"));
        assertThat(ordersForQuote(quoteId)).isOne();
    }

    @RepeatedTest(5)
    void acceptanceRacingANewRevisionNeverLetsBothSucceed() throws Exception {
        String requestId = requests.submitted(alice, "contested.stl");
        String quoteId = requests.quoted(requestId, 50_000);
        String newFile = files.uploadChecked(alice, "contested-v2.stl", StlFixtures.binary(7));

        List<MvcResult> results = concurrently(
                () -> requests.accept(alice, quoteId).andReturn(),
                () -> mvc.perform(post("/api/requests/{id}/revisions", requestId).with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"artifactId": "%s", "material": "PLA", "colour": "Blue", "notes": ""}
                                """.formatted(newFile))).andReturn());

        int accepted = results.get(0).getResponse().getStatus();
        int revised = results.get(1).getResponse().getStatus();
        String quoteStatus = jdbc.sql("SELECT status FROM quote WHERE id = ?::uuid").param(quoteId)
                .query(String.class).single();
        if (accepted == 200) {
            assertThat(revised).isEqualTo(409);
            assertThat(results.get(1).getResponse().getContentAsString()).contains("REQUEST_CLOSED");
            assertThat(quoteStatus).isEqualTo("ACCEPTED");
            assertThat(ordersForQuote(quoteId)).isOne();
        } else {
            assertThat(revised).isEqualTo(201);
            assertThat(accepted).isEqualTo(409);
            assertThat(results.get(0).getResponse().getContentAsString()).contains("QUOTE_WITHDRAWN");
            assertThat(quoteStatus).isEqualTo("WITHDRAWN");
            assertThat(ordersForQuote(quoteId)).isZero();
        }
    }

    @Test
    void anExpiredQuoteCannotBeAccepted() throws Exception {
        String requestId = requests.submitted(alice, "late.stl");
        String quoteId = requests.quoted(requestId, 50_000);
        jdbc.sql("""
                        UPDATE quote SET issued_at = now() - interval '8 days', expires_at = now() - interval '1 second'
                        WHERE id = ?::uuid""")
                .param(quoteId).update();

        mvc.perform(get("/api/requests/{id}/quotes", requestId).with(alice))
                .andExpect(jsonPath("$[0].expired").value(true))
                .andExpect(jsonPath("$[0].acceptable").value(false));
        requests.accept(alice, quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));

        assertThat(ordersForQuote(quoteId)).isZero();
        assertThat(quoteStatus(quoteId)).isEqualTo("ISSUED");
    }

    @Test
    void replacingTheModelWithdrawsTheQuoteAndTheNewRevisionHasNoApproval() throws Exception {
        String requestId = requests.submitted(alice, "v1.stl");
        String firstRevision = requests.latestRevisionId(requestId);
        String quoteId = requests.quoted(requestId, 50_000);

        requests.revise(alice, requestId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.revisions[1].number").value(2));

        assertThat(quoteStatus(quoteId)).isEqualTo("WITHDRAWN");
        mvc.perform(get("/api/requests/{id}/quotes", requestId).with(alice))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$[0].acceptable").value(false));
        requests.accept(alice, quoteId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_WITHDRAWN"));
        // The old revision cannot be quoted any more; only the new one can.
        requests.quoteRequest(requestId, firstRevision, 50_000, 7)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_NOT_LATEST"));
        assertThat(ordersForQuote(quoteId)).isZero();
    }

    @Test
    void reQuotingWithdrawsTheEarlierQuote() throws Exception {
        String requestId = requests.submitted(alice, "requote.stl");
        String first = requests.quoted(requestId, 50_000);
        String second = requests.quoted(requestId, 45_000);

        assertThat(quoteStatus(first)).isEqualTo("WITHDRAWN");
        assertThat(quoteStatus(second)).isEqualTo("ISSUED");
        requests.accept(alice, first).andExpect(status().isConflict());
        requests.accept(alice, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.amountMinor").value(45_000));
    }

    @Test
    void onlyReviewableRequestsCanBeQuoted() throws Exception {
        String requestId = requests.submitted(alice, "changes.stl");
        String revisionId = requests.latestRevisionId(requestId);
        mvc.perform(post("/api/ops/requests/{id}/request-changes", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"Add fillets\"}"))
                .andExpect(status().isOk());

        requests.quoteRequest(requestId, revisionId, 50_000, 7)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_QUOTABLE"));
        requests.quoteRequest(requestId, revisionId, 50_000, 15)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validDays"));
    }

    @Test
    void anotherCustomerCannotSeeOrAcceptTheQuote() throws Exception {
        String requestId = requests.submitted(alice, "private.stl");
        String quoteId = requests.quoted(requestId, 50_000);

        requests.accept(bob, quoteId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        mvc.perform(get("/api/requests/{id}/quotes", requestId).with(bob)).andExpect(status().isNotFound());
        assertThat(quoteStatus(quoteId)).isEqualTo("ISSUED");
    }

    @Test
    void aLongFileNameIsShortenedToFitTheOrderLine() throws Exception {
        String filename = "a".repeat(196) + ".stl"; // 200 characters, the longest allowed
        String quoteId = requests.quoted(requests.submitted(alice, filename), 50_000);

        String productName = JsonPath.read(requests.accept(alice, quoteId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.line.productName");

        assertThat(productName).hasSize(120).startsWith("Custom part: aaa").endsWith("…");
    }

    @Test
    void aCustomOrderGoesThroughProductionToReadyForHandoff() throws Exception {
        String printerId = new ProductionFixtures(jdbc).clearPrinter().toString();
        ApiSteps api = new ApiSteps(mvc, worker, observer, simulator);
        String quoteId = requests.quoted(requests.submitted(alice, "production.stl"), 50_000);
        String body = requests.accept(alice, quoteId).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");
        String recipeId = JsonPath.read(body, "$.line.recipeRevisionId");

        mvc.perform(post("/api/demo/orders/{id}/simulate-payment", orderId).with(alice)).andExpect(status().isOk());
        String jobId = api.release(orderId);
        api.get("/api/ops/jobs/" + jobId).andExpect(jsonPath("$.recipeRevisionId").value(recipeId));
        String attemptId = api.startAttempt(jobId, printerId);
        api.complete(attemptId);
        api.qc(attemptId, "PASSED").andExpect(jsonPath("$.status").value("ACCEPTED"));
        api.post("/api/ops/orders/" + orderId + "/ready-for-handoff").andExpect(status().isOk());

        mvc.perform(get("/api/orders/{id}", orderId).with(alice))
                .andExpect(jsonPath("$.status").value("READY_FOR_HANDOFF"));
    }

    // --- helpers -------------------------------------------------------------

    /** The accepted scope, followed from the order back to the stored file version. */
    private Map<String, Object> acceptedScope(String orderId) {
        return jdbc.sql("""
                        SELECT l.quote_id, l.recipe_revision_id, q.revision_id, s.material, s.notes, s.artifact_id,
                               a.version_id, a.sha256, r.quote_id AS recipe_quote_id
                        FROM order_line l
                        JOIN quote q ON q.id = l.quote_id
                        JOIN submission_revision s ON s.id = q.revision_id
                        JOIN artifact a ON a.id = s.artifact_id
                        JOIN recipe_revision r ON r.id = l.recipe_revision_id
                        WHERE l.order_id = ?::uuid""")
                .param(orderId).query().singleRow();
    }

    private long ordersForQuote(String quoteId) {
        return jdbc.sql("SELECT count(*) FROM order_line WHERE quote_id = ?::uuid")
                .param(quoteId).query(Long.class).single();
    }

    private String quoteStatus(String quoteId) {
        return jdbc.sql("SELECT status FROM quote WHERE id = ?::uuid").param(quoteId).query(String.class).single();
    }

    @SafeVarargs
    private static List<MvcResult> concurrently(Callable<MvcResult>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = java.util.Arrays.stream(calls).map(call -> pool.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            List<MvcResult> results = new java.util.ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
