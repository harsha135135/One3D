package store.one3d.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Custom requests and quotes through the API: customers as given, operators as the default test user. */
public final class RequestSteps {

    private final MockMvc mvc;
    private final FileSteps files;

    public RequestSteps(MockMvc mvc, FileSteps files) {
        this.mvc = mvc;
        this.files = files;
    }

    /** Uploads a checked file and submits it; returns the request id. */
    public String submitted(RequestPostProcessor customer, String filename) throws Exception {
        String artifactId = files.uploadChecked(customer, filename, StlFixtures.binary(4));
        String body = mvc.perform(post("/api/requests").with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requirements(artifactId, "PETG", "Orange")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Uploads a new checked file and submits it as the next revision. */
    public ResultActions revise(RequestPostProcessor customer, String requestId) throws Exception {
        String artifactId = files.uploadChecked(customer, "revised-" + UUID.randomUUID() + ".stl", StlFixtures.binary(6));
        return mvc.perform(post("/api/requests/{id}/revisions", requestId).with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requirements(artifactId, "PLA", "Blue")));
    }

    public String latestRevisionId(String requestId) throws Exception {
        String body = mvc.perform(get("/api/ops/requests/{id}", requestId)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.revisions[-1].id");
    }

    public ResultActions quoteRequest(String requestId, String revisionId, long priceMinor, int validDays)
            throws Exception {
        return mvc.perform(post("/api/ops/requests/{id}/quotes", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"revisionId": "%s", "priceMinor": %d, "validDays": %d, "material": "PETG",
                         "layerHeightMicrons": 200, "recipeNotes": "3 walls, 20%% gyroid infill"}
                        """.formatted(revisionId, priceMinor, validDays)));
    }

    /** Quotes the latest revision; returns the quote id. */
    public String quoted(String requestId, long priceMinor) throws Exception {
        String body = quoteRequest(requestId, latestRevisionId(requestId), priceMinor, 7)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    public ResultActions accept(RequestPostProcessor customer, String quoteId) throws Exception {
        return mvc.perform(post("/api/quotes/{id}/accept", quoteId).with(customer));
    }

    private static String requirements(String artifactId, String material, String colour) {
        return """
                {"artifactId": "%s", "material": "%s", "colour": "%s", "notes": "Functional part"}
                """.formatted(artifactId, material, colour);
    }
}
