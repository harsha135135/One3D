package store.one3d.files;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Private object storage for uploaded files. The browser transfers file bytes
 * directly to storage with short-lived presigned URLs; the backend only signs
 * them, checks what arrived and reads exact versions.
 */
interface ObjectStorage {

    /** A URL the browser may PUT exactly {@code contentLength} bytes to, with these headers, until expiry. */
    record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
    }

    /** What is stored at a key now. {@code versionId} is empty if the bucket is not versioned. */
    record StoredObject(Optional<String> versionId, long sizeBytes) {
    }

    PresignedUpload presignUpload(String key, long contentLength, Duration ttl);

    /** The current object at the key, or empty if nothing was uploaded. */
    Optional<StoredObject> head(String key);

    /** Opens one exact version for reading. The caller closes the stream. */
    InputStream openVersion(String key, String versionId);

    /**
     * A short-lived URL for one exact version, served as a download
     * ({@code application/octet-stream}, attachment) so a browser never renders it.
     */
    URI presignDownload(String key, String versionId, String filename, Duration ttl);
}
