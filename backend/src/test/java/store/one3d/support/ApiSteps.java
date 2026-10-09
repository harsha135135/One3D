package store.one3d.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import store.one3d.production.DispatchWorker;
import store.one3d.production.ExecutionObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP calls of the order and production workflow, so integration tests
 * read as a sequence of steps. Each step that must succeed asserts its status.
 *
 * <p>Printer results come from the real simulator: {@link #startAttempt} lets
 * the dispatch worker deliver the command, and {@link #complete}/{@link #fail}
 * end the print with the simulator's dev controls and let the observer record it.
 */
public final class ApiSteps {

    public static final String DEMO_CONFIGURATION_ID = "0d3d0000-0000-4000-8000-000000000002";

    private final MockMvc mvc;
    private final DispatchWorker worker;
    private final ExecutionObserver observer;
    private final SimulatorRig simulator;
    private final Map<String, String> printerOfAttempt = new HashMap<>();

    public ApiSteps(MockMvc mvc, DispatchWorker worker, ExecutionObserver observer, SimulatorRig simulator) {
        this.mvc = mvc;
        this.worker = worker;
        this.observer = observer;
        this.simulator = simulator;
    }

    /** Places an order for the seeded demo configuration and returns its id. */
    public String placeOrder() throws Exception {
        String body = post("/api/orders", """
                {"productConfigurationId": "%s", "quantity": 1}
                """.formatted(DEMO_CONFIGURATION_ID))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Places an order and confirms it with the demo-only simulated payment. */
    public String placeConfirmedOrder() throws Exception {
        String orderId = placeOrder();
        post("/api/demo/orders/" + orderId + "/simulate-payment").andExpect(status().isOk());
        return orderId;
    }

    /** Places, confirms and releases an order; returns the job id. */
    public String releasedJob() throws Exception {
        return release(placeConfirmedOrder());
    }

    public String release(String orderId) throws Exception {
        String body = post("/api/ops/orders/" + orderId + "/release")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * Records the start of the next attempt (201) and lets the dispatch worker
     * deliver it. Returns the attempt id; it is RUNNING unless the simulator
     * refused it or the reply was lost.
     */
    public String startAttempt(String jobId, String printerId) throws Exception {
        String attemptId = recordStart(jobId, printerId);
        dispatch();
        return attemptId;
    }

    /** Only records the start (attempt START_INTENT); the simulator is not contacted. */
    public String recordStart(String jobId, String printerId) throws Exception {
        String body = startAttemptRequest(jobId, printerId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attemptId = JsonPath.read(body, "$.attempts[-1].id");
        printerOfAttempt.put(attemptId, printerId);
        return attemptId;
    }

    public ResultActions startAttemptRequest(String jobId, String printerId) throws Exception {
        return post("/api/ops/jobs/" + jobId + "/attempts", """
                {"printerId": "%s"}
                """.formatted(printerId));
    }

    /** One run of the dispatch worker. */
    public void dispatch() {
        worker.runOnce();
    }

    /** One poll of running attempts. */
    public void observe() {
        observer.observeOnce();
    }

    /** The simulated printer finishes the attempt's print; the backend observes it. */
    public void complete(String attemptId) {
        simulator.complete(printerOfAttempt.get(attemptId));
        observe();
    }

    /** The simulated printer reports a failed print; the backend observes it. */
    public void fail(String attemptId, String reason) {
        simulator.fail(printerOfAttempt.get(attemptId), reason);
        observe();
    }

    public ResultActions qc(String attemptId, String result) throws Exception {
        return post("/api/ops/attempts/" + attemptId + "/qc", """
                {"result": "%s", "notes": "Whole-part inspection"}
                """.formatted(result));
    }

    /** An operator clears the print currently on the bed. */
    public void clearBed(String printerId) throws Exception {
        clearBedRequest(printerId, bedCycleAttemptId(printerId)).andExpect(status().isOk());
    }

    public ResultActions clearBedRequest(String printerId, String clearedAttemptId) throws Exception {
        return post("/api/ops/printers/" + printerId + "/clear-bed", """
                {"attemptId": "%s"}
                """.formatted(clearedAttemptId));
    }

    /** The attempt whose print is on the printer's bed, or null if the bed is clear. */
    public String bedCycleAttemptId(String printerId) throws Exception {
        List<String> ids = JsonPath.read(printersJson(), "$[?(@.id == '" + printerId + "')].bedCycleAttemptId");
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public String printersJson() throws Exception {
        return get("/api/ops/printers").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    public String jobJson(String jobId) throws Exception {
        return get("/api/ops/jobs/" + jobId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    public ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url));
    }

    public ResultActions post(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url));
    }

    public ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
