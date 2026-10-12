package store.one3d.files;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import store.one3d.background.PendingWorkStore;
import store.one3d.background.WorkKind;
import store.one3d.support.FileSteps;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.StlFixtures;
import store.one3d.support.StorageRig;
import store.one3d.support.TestUsers;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Private uploads end to end against PostgreSQL and S3Mock: the test acts as
 * the browser and PUTs to the presigned URL the API returns.
 */
@PostgresIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class FilesIT {

    private static final long LIMIT = 20_971_520;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StorageRig storage;

    @Autowired
    ArtifactVerifier verifier;

    @Autowired
    ArtifactVerification verification;

    @Autowired
    PendingWorkStore workStore;

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
    void aBinaryModelIsUploadedPinnedAndVerified() throws Exception {
        byte[] model = StlFixtures.binary(20);
        FileSteps.Upload upload = files.startUpload(alice, "bracket v2.stl", model.length);
        files.put(upload, model);

        String finalized = files.finalizeRequest(alice, upload.artifactId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFYING"))
                .andReturn().getResponse().getContentAsString();
        String versionId = JsonPath.read(finalized, "$.versionId");
        assertThat(versionId).isNotBlank();

        files.verify();

        mvc.perform(get("/api/files/{id}", upload.artifactId()).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.filename").value("bracket v2.stl"))
                .andExpect(jsonPath("$.sizeBytes").value(model.length))
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.sha256").value(StlFixtures.sha256(model)));
    }

    @Test
    void anAsciiModelIsVerified() throws Exception {
        String artifactId = files.uploadChecked(alice, "cube.stl", StlFixtures.ascii());

        mvc.perform(get("/api/files/{id}", artifactId).with(alice))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void contentThatIsNotAnStlModelIsRejected() throws Exception {
        String artifactId = files.uploadChecked(alice, "page.stl", StlFixtures.htmlPretendingToBeStl());

        mvc.perform(get("/api/files/{id}", artifactId).with(alice))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.startsWith("Not a valid STL file")))
                .andExpect(jsonPath("$.sha256").doesNotExist());
        mvc.perform(get("/api/files/{id}/download", artifactId).with(alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_NOT_VERIFIED"));
    }

    @Test
    void anOversizedOrNonStlUploadIsRefusedBeforeAnythingIsStored() throws Exception {
        long before = artifactCount();

        files.startUploadRequest(alice, "huge.stl", LIMIT + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        files.startUploadRequest(alice, "model.obj", 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_NAME"));
        files.startUploadRequest(alice, "x".repeat(197) + ".stl", 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(artifactCount()).isEqualTo(before);
    }

    @Test
    void theLargestAllowedDeclarationIsAccepted() throws Exception {
        files.startUploadRequest(alice, "max.stl", LIMIT).andExpect(status().isCreated());
    }

    @Test
    void finalizeChecksWhatWasActuallyStored() throws Exception {
        byte[] model = StlFixtures.binary(4);
        FileSteps.Upload upload = files.startUpload(alice, "part.stl", model.length);

        files.finalizeRequest(alice, upload.artifactId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        // S3 itself refuses a body that differs from the signed Content-Length;
        // the mock does not, so this proves the application's own check.
        files.put(upload, StlFixtures.binary(5));
        files.finalizeRequest(alice, upload.artifactId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_SIZE_MISMATCH"));
        assertThat(artifactStatus(upload.artifactId())).isEqualTo("UPLOAD_PENDING");
        assertThat(workRows(upload.artifactId())).isZero();
    }

    @Test
    void finalizingTwiceQueuesOneCheckAndKeepsThePinnedVersion() throws Exception {
        byte[] model = StlFixtures.binary(2);
        FileSteps.Upload upload = files.startUpload(alice, "twice.stl", model.length);
        files.put(upload, model);

        String first = files.finalizeRequest(alice, upload.artifactId()).andReturn().getResponse().getContentAsString();
        String second = files.finalizeRequest(alice, upload.artifactId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.versionId")).isEqualTo(JsonPath.read(first, "$.versionId"));
        assertThat(workRows(upload.artifactId())).isOne();
    }

    @Test
    void anUploadCannotBeFinalizedAfterItsWindowCloses() throws Exception {
        byte[] model = StlFixtures.binary(2);
        FileSteps.Upload upload = files.startUpload(alice, "late.stl", model.length);
        files.put(upload, model);
        jdbc.sql("UPDATE artifact SET upload_expires_at = now() - interval '1 second' WHERE id = ?::uuid")
                .param(upload.artifactId()).update();

        files.finalizeRequest(alice, upload.artifactId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_EXPIRED"));
    }

    @Test
    void anotherCustomerCannotFinalizeReadOrDownloadTheFile() throws Exception {
        byte[] model = StlFixtures.binary(3);
        FileSteps.Upload upload = files.startUpload(alice, "private.stl", model.length);
        files.put(upload, model);

        files.finalizeRequest(bob, upload.artifactId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
        assertThat(artifactStatus(upload.artifactId())).isEqualTo("UPLOAD_PENDING");

        files.finalizeRequest(alice, upload.artifactId()).andExpect(status().isOk());
        files.verify();

        mvc.perform(get("/api/files/{id}", upload.artifactId()).with(bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/{id}/download", upload.artifactId()).with(bob))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void theDownloadIsTheExactCheckedVersionServedAsAnAttachment(CapturedOutput output) throws Exception {
        byte[] original = StlFixtures.binary(6);
        FileSteps.Upload upload = files.startUpload(alice, "pinned.stl", original.length);
        files.put(upload, original);
        files.finalizeRequest(alice, upload.artifactId()).andExpect(status().isOk());
        files.verify();
        String pinned = JsonPath.read(mvc.perform(get("/api/files/{id}", upload.artifactId()).with(alice))
                .andReturn().getResponse().getContentAsString(), "$.versionId");

        // The presigned URL is still valid: a second PUT replaces the object at the same key.
        byte[] replacement = StlFixtures.binary(6, "replacement");
        files.put(upload, replacement);

        String location = mvc.perform(get("/api/files/{id}/download", upload.artifactId()).with(alice))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn().getResponse().getHeader("Location");
        // A boolean check, so a failure never prints the signed URL.
        assertThat(location.contains("versionId=" + pinned)).as("download link pins the checked version").isTrue();

        HttpResponse<byte[]> downloaded = storage.download(URI.create(location));
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(original);
        assertThat(downloaded.headers().firstValue("Content-Type")).contains("application/octet-stream");
        assertThat(downloaded.headers().firstValue("Content-Disposition")).contains("attachment; filename=\"pinned.stl\"");

        mvc.perform(get("/api/files/{id}", upload.artifactId()).with(alice))
                .andExpect(jsonPath("$.versionId").value(pinned))
                .andExpect(jsonPath("$.sha256").value(StlFixtures.sha256(original)));
        // Nothing logged during the upload, check and download contains a signed URL.
        assertThat(output.getAll().contains("X-Amz-Signature")).as("a signature in the logs").isFalse();
        assertThat(output.getAll().contains("X-Amz-Credential")).as("a credential in the logs").isFalse();
    }

    @Test
    void aLateVerifierCannotOverwriteTheResultOfTheVerifierThatReplacedIt() throws Exception {
        String artifactId = files.uploadAndFinalize(alice, "race.stl", StlFixtures.binary(2));
        PendingWorkStore.ClaimedWork late = workStore.claim(WorkKind.VERIFY_ARTIFACT, 50, Duration.ofSeconds(30))
                .stream().filter(work -> work.subjectId().toString().equals(artifactId)).findFirst().orElseThrow();
        jdbc.sql("UPDATE pending_work SET claimed_until = now() - interval '1 second' WHERE id = ?")
                .param(late.id()).update();

        files.verify(); // the replacement verifier checks the file
        assertThat(artifactStatus(artifactId)).isEqualTo("VERIFIED");

        boolean applied = verification.apply(late, ArtifactVerification.Outcome.failed("late result"));

        assertThat(applied).isFalse();
        assertThat(artifactStatus(artifactId)).isEqualTo("VERIFIED");
    }

    @Test
    void anOperatorCanRetryACheckWhoseTriesRanOut() throws Exception {
        String artifactId = files.uploadAndFinalize(alice, "retry.stl", StlFixtures.binary(2));
        mvc.perform(post("/api/ops/files/{id}/retry-verification", artifactId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_RETRY"));
        jdbc.sql("""
                        UPDATE pending_work SET status = 'EXHAUSTED', attempts = max_attempts,
                               last_error = 'Storage read failed'
                        WHERE artifact_id = ?::uuid""")
                .param(artifactId).update();
        files.verify();
        assertThat(artifactStatus(artifactId)).isEqualTo("VERIFYING");

        mvc.perform(post("/api/ops/files/{id}/retry-verification", artifactId)).andExpect(status().isOk());
        files.verify();

        assertThat(artifactStatus(artifactId)).isEqualTo("VERIFIED");
    }

    private long artifactCount() {
        return jdbc.sql("SELECT count(*) FROM artifact").query(Long.class).single();
    }

    private String artifactStatus(String artifactId) {
        return jdbc.sql("SELECT status FROM artifact WHERE id = ?::uuid").param(artifactId).query(String.class).single();
    }

    private long workRows(String artifactId) {
        return jdbc.sql("SELECT count(*) FROM pending_work WHERE artifact_id = ?::uuid")
                .param(artifactId).query(Long.class).single();
    }
}
