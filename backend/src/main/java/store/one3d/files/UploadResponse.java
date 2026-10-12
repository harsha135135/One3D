package store.one3d.files;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * The new file record plus where to send its bytes: PUT the file to
 * {@code upload.url} with exactly {@code upload.headers}, then finalize.
 */
record UploadResponse(ArtifactResponse artifact, Upload upload) {

    record Upload(String method, URI url, Map<String, String> headers, Instant expiresAt, long maxBytes) {
    }
}
