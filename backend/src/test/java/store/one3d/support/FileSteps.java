package store.one3d.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import store.one3d.files.ArtifactVerifier;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploads files the way the browser does: ask the API for an upload, PUT the
 * bytes to the presigned URL it returned, then finalize and let the verifier run.
 */
public final class FileSteps {

    private final MockMvc mvc;
    private final StorageRig storage;
    private final ArtifactVerifier verifier;

    public FileSteps(MockMvc mvc, StorageRig storage, ArtifactVerifier verifier) {
        this.mvc = mvc;
        this.storage = storage;
        this.verifier = verifier;
    }

    /** An upload as returned by {@code POST /api/files/uploads}. */
    public record Upload(String artifactId, URI url, Map<String, String> headers) {
    }

    public ResultActions startUploadRequest(RequestPostProcessor user, String filename, long sizeBytes) throws Exception {
        return mvc.perform(post("/api/files/uploads").with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"filename": "%s", "sizeBytes": %d}
                        """.formatted(filename, sizeBytes)));
    }

    public Upload startUpload(RequestPostProcessor user, String filename, long sizeBytes) throws Exception {
        String body = startUploadRequest(user, filename, sizeBytes)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Upload(JsonPath.read(body, "$.artifact.id"), URI.create(JsonPath.read(body, "$.upload.url")),
                JsonPath.read(body, "$.upload.headers"));
    }

    /** PUTs the bytes to the presigned URL, as the browser would. */
    public void put(Upload upload, byte[] content) {
        assertThat(storage.upload(upload.url(), upload.headers(), content)).isEqualTo(200);
    }

    public ResultActions finalizeRequest(RequestPostProcessor user, String artifactId) throws Exception {
        return mvc.perform(post("/api/files/{id}/finalize", artifactId).with(user));
    }

    /** Uploads and finalizes; the check has not run yet (VERIFYING). */
    public String uploadAndFinalize(RequestPostProcessor user, String filename, byte[] content) throws Exception {
        Upload upload = startUpload(user, filename, content.length);
        put(upload, content);
        finalizeRequest(user, upload.artifactId()).andExpect(status().isOk());
        return upload.artifactId();
    }

    /** Uploads, finalizes and runs the check. */
    public String uploadChecked(RequestPostProcessor user, String filename, byte[] content) throws Exception {
        String artifactId = uploadAndFinalize(user, filename, content);
        verify();
        return artifactId;
    }

    public int verify() {
        return verifier.runOnce();
    }
}
