package store.one3d.files;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** {@link ObjectStorage} on S3 (or an S3-compatible mock locally). Never logs signed URLs. */
@Component
class S3ObjectStorage implements ObjectStorage {

    /** Uploaded models are stored and served as opaque bytes, never as something a browser renders. */
    static final String CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public PresignedUpload presignUpload(String key, long contentLength, Duration ttl) {
        // Content-Length and Content-Type are part of the signature: on S3 an
        // upload of any other size or type is refused.
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentLength(contentLength).contentType(CONTENT_TYPE).build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(r -> r.signatureDuration(ttl).putObjectRequest(put));
        return new PresignedUpload(toUri(signed.url()), Map.of("Content-Type", CONTENT_TYPE), signed.expiration());
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(r -> r.bucket(bucket).key(key));
            String version = head.versionId();
            return Optional.of(new StoredObject(
                    version == null || version.isBlank() || version.equals("null") ? Optional.empty() : Optional.of(version),
                    head.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageUnavailableException("Storage HEAD failed with status " + e.statusCode(), e);
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage HEAD failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public InputStream openVersion(String key, String versionId) {
        try {
            return s3.getObject(r -> r.bucket(bucket).key(key).versionId(versionId));
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage GET failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public URI presignDownload(String key, String versionId, String filename, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket).key(key).versionId(versionId)
                .responseContentType(CONTENT_TYPE)
                .responseContentDisposition("attachment; filename=\"" + filename + "\"")
                .build();
        return toUri(presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(get)).url());
    }

    private static URI toUri(java.net.URL url) {
        try {
            return url.toURI();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Presigner returned an invalid URL", e);
        }
    }
}
