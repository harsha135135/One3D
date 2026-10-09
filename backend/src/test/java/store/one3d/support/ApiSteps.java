package store.one3d.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP calls of the order and production workflow, so integration tests
 * read as a sequence of steps. Each step that must succeed asserts its status.
 */
public final class ApiSteps {

    public static final String DEMO_CONFIGURATION_ID = "0d3d0000-0000-4000-8000-000000000002";

    private final MockMvc mvc;

    public ApiSteps(MockMvc mvc) {
        this.mvc = mvc;
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

    /** Starts the next attempt and returns its id. */
    public String startAttempt(String jobId, String printerId) throws Exception {
        String body = startAttemptRequest(jobId, printerId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.attempts[-1].id");
    }

    public ResultActions startAttemptRequest(String jobId, String printerId) throws Exception {
        return post("/api/ops/jobs/" + jobId + "/attempts", """
                {"printerId": "%s"}
                """.formatted(printerId));
    }

    public void complete(String attemptId) throws Exception {
        post("/api/ops/attempts/" + attemptId + "/simulate-completion").andExpect(status().isOk());
    }

    public void fail(String attemptId, String reason) throws Exception {
        post("/api/ops/attempts/" + attemptId + "/simulate-failure", """
                {"reason": "%s"}
                """.formatted(reason))
                .andExpect(status().isOk());
    }

    public ResultActions qc(String attemptId, String result) throws Exception {
        return post("/api/ops/attempts/" + attemptId + "/qc", """
                {"result": "%s", "notes": "Whole-part inspection"}
                """.formatted(result));
    }

    public void clearBed(String printerId) throws Exception {
        post("/api/ops/printers/" + printerId + "/clear-bed").andExpect(status().isOk());
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
