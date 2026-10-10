package store.one3d.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/** Settings under {@code one3d.storage}; see application.yaml. */
@ConfigurationProperties("one3d.storage")
record StorageProperties(
        String bucket,
        String region,
        String endpoint,
        String publicEndpoint,
        boolean pathStyle,
        String accessKey,
        String secretKey,
        Duration uploadTtl,
        Duration downloadTtl) {

    /** The backend's own address for storage; empty means AWS S3. */
    Optional<URI> endpointUri() {
        return uri(endpoint);
    }

    /** The address presigned URLs point at; empty means AWS S3. */
    Optional<URI> publicEndpointUri() {
        return uri(publicEndpoint);
    }

    boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank();
    }

    private static Optional<URI> uri(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(URI.create(value));
    }
}
