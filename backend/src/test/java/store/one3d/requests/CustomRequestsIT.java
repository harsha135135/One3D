package store.one3d.requests;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import store.one3d.files.ArtifactVerifier;
import store.one3d.support.FileSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.StlFixtures;
import store.one3d.support.StorageRig;
import store.one3d.support.TestUsers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom requests: submission revisions, operator review and the rule that a
 * new revision starts review again with no approval carried over.
 */
@PostgresIntegrationTest
class CustomRequestsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StorageRig storage;

    @Autowired
    ArtifactVerifier verifier;

    private FileSteps files;
    private RequestPostProcessor alice;
    private RequestPostProcessor bob;

    @BeforeEach
    void setUp() {
        files = new FileSteps(mvc, storage, verifier);
        alice = TestUsers.customer("alice-" + UUID.randomUUID());
        bob = TestUsers.customer("bob-" + UUID.randomUUID());
    }

    @Test
    void aCustomerSubmitsACheckedFileWithRequirements() throws Exception {
        byte[] model = StlFixtures.binary(8);
        String artifactId = files.uploadChecked(alice, "gear.stl", model);

        String body = submit(alice, artifactId, "PETG", "Black", "Teeth must mesh")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.revisions", hasSize(1)))
                .andExpect(jsonPath("$.revisions[0].number").value(1))
                .andExpect(jsonPath("$.revisions[0].material").value("PETG"))
                .andExpect(jsonPath("$.revisions[0].file.id").value(artifactId))
                .andExpect(jsonPath("$.revisions[0].file.sha256").value(StlFixtures.sha256(model)))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/requests").with(alice))
                .andExpect(jsonPath("$.items[0].id").value(requestId))
                .andExpect(jsonPath("$.items[0].filename").value("gear.stl"))
                .andExpect(jsonPath("$.items[0].latestRevision").value(1));
        mvc.perform(get("/api/ops/requests?status=SUBMITTED&size=50"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheCustomersOwnCheckedFileCanBeSubmittedOnce() throws Exception {
        String unchecked = files.uploadAndFinalize(alice, "pending.stl", StlFixtures.binary(2));
        submit(alice, unchecked, "PLA", "White", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_NOT_VERIFIED"));

        String alicesFile = files.uploadChecked(alice, "mine.stl", StlFixtures.binary(2));
        submit(bob, alicesFile, "PLA", "White", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));

        submit(alice, alicesFile, "PLA", "White", "").andExpect(status().isCreated());
        submit(alice, alicesFile, "PLA", "Red", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_ALREADY_SUBMITTED"));
    }

    @Test
    void requirementsLongerThanTheirColumnsAreRefused() throws Exception {
        String artifactId = files.uploadChecked(alice, "long.stl", StlFixtures.binary(2));

        submit(alice, artifactId, "P".repeat(81), "White", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("material"));
        submit(alice, artifactId, "PLA", "White", "n".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("notes"));
    }

    @Test
    void theOperatorAsksForChangesAndTheNewRevisionIsReviewedAgain() throws Exception {
        String requestId = submittedRequest(alice);

        mvc.perform(post("/api/ops/requests/{id}/request-changes", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Wall thickness under 1 mm; please thicken.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUIRED"));
        mvc.perform(get("/api/requests/{id}", requestId).with(alice))
                .andExpect(jsonPath("$.operatorMessage").value("Wall thickness under 1 mm; please thicken."));

        String newFile = files.uploadChecked(alice, "thicker.stl", StlFixtures.binary(3));
        revise(alice, requestId, newFile)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.operatorMessage").doesNotExist())
                .andExpect(jsonPath("$.revisions", hasSize(2)))
                .andExpect(jsonPath("$.revisions[1].number").value(2))
                .andExpect(jsonPath("$.revisions[1].file.id").value(newFile));
    }

    @Test
    void aRejectedRequestTakesNoMoreRevisions() throws Exception {
        String requestId = submittedRequest(alice);
        mvc.perform(post("/api/ops/requests/{id}/reject", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"We cannot print this geometry.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        String newFile = files.uploadChecked(alice, "again.stl", StlFixtures.binary(3));
        revise(alice, requestId, newFile)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
    }

    @Test
    void anotherCustomerCannotSeeOrReviseTheRequest() throws Exception {
        String requestId = submittedRequest(alice);
        String bobsFile = files.uploadChecked(bob, "bob.stl", StlFixtures.binary(2));

        mvc.perform(get("/api/requests/{id}", requestId).with(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        revise(bob, requestId, bobsFile).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/{id}/quotes", requestId).with(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests").with(bob))
                .andExpect(jsonPath("$.items[?(@.id == '" + requestId + "')]").isEmpty());
        assertThat(revisionCount(requestId)).isOne();
    }

    @Test
    void theOperatorDownloadsTheExactFileOfARevision() throws Exception {
        byte[] model = StlFixtures.binary(5);
        String artifactId = files.uploadChecked(alice, "review.stl", model);
        String requestId = JsonPath.read(submit(alice, artifactId, "PLA", "Grey", "")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String versionId = jdbc.sql("SELECT version_id FROM artifact WHERE id = ?::uuid")
                .param(artifactId).query(String.class).single();

        String location = mvc.perform(get("/api/ops/requests/{id}/revisions/1/file", requestId))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).contains("versionId=" + versionId);
        assertThat(storage.download(java.net.URI.create(location)).body()).isEqualTo(model);
        mvc.perform(get("/api/ops/requests/{id}/revisions/2/file", requestId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));
    }

    // --- helpers -------------------------------------------------------------

    private String submittedRequest(RequestPostProcessor customer) throws Exception {
        String artifactId = files.uploadChecked(customer, "part-" + UUID.randomUUID() + ".stl", StlFixtures.binary(4));
        return JsonPath.read(submit(customer, artifactId, "PLA", "White", "")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions submit(RequestPostProcessor customer, String artifactId, String material, String colour,
                                 String notes) throws Exception {
        return mvc.perform(post("/api/requests").with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requirements(artifactId, material, colour, notes)));
    }

    private ResultActions revise(RequestPostProcessor customer, String requestId, String artifactId) throws Exception {
        return mvc.perform(post("/api/requests/{id}/revisions", requestId).with(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requirements(artifactId, "PLA", "White", "revised")));
    }

    private static String requirements(String artifactId, String material, String colour, String notes) {
        return """
                {"artifactId": "%s", "material": "%s", "colour": "%s", "notes": "%s"}
                """.formatted(artifactId, material, colour, notes);
    }

    private long revisionCount(String requestId) {
        return jdbc.sql("SELECT count(*) FROM submission_revision WHERE request_id = ?::uuid")
                .param(requestId).query(Long.class).single();
    }
}
