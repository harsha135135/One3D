package store.one3d.support;

import org.springframework.beans.factory.DisposableBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Private object storage for integration tests: the same S3Mock image as
 * compose.yaml, as a separate container, with the bucket created and
 * versioning switched on the same way compose.yaml does it. The application
 * never creates or configures the bucket itself.
 *
 * <p>S3Mock does not check signatures, expiry or the signed Content-Length.
 * Tests here prove the application's own checks; real S3 enforcement is
 * verified against AWS (Checkpoint 7).
 */
public final class StorageRig implements DisposableBean {

    public static final String IMAGE = "adobe/s3mock:5.2";
    public static final String BUCKET = "one3d-artifacts";
    private static final int PORT = 9090;

    private final GenericContainer<?> container;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public StorageRig() {
        container = new GenericContainer<>(IMAGE)
                .withExposedPorts(PORT)
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
                .waitingFor(Wait.forHttp("/").forPort(PORT).withStartupTimeout(Duration.ofSeconds(60)));
        container.start();
        HttpResponse<String> versioning = send(HttpRequest.newBuilder(endpointUri().resolve("/" + BUCKET + "?versioning"))
                .header("Content-Type", "application/xml")
                .PUT(HttpRequest.BodyPublishers.ofString("""
                        <VersioningConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">\
                        <Status>Enabled</Status></VersioningConfiguration>""")));
        if (versioning.statusCode() != 200) {
            throw new IllegalStateException("Could not enable versioning: " + versioning.statusCode());
        }
    }

    /** The storage address, used by the backend and (in tests) by the "browser" too. */
    public String endpoint() {
        return endpointUri().toString();
    }

    /** Sends bytes to a presigned upload URL the way a browser would. Returns the HTTP status. */
    public int upload(URI presignedUrl, Map<String, String> headers, byte[] body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(presignedUrl).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return send(request).statusCode();
    }

    /** Follows a download link. Returns the response, body as bytes. */
    public HttpResponse<byte[]> download(URI signedUrl) {
        try {
            return http.send(HttpRequest.newBuilder(signedUrl).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void destroy() {
        container.stop();
    }

    private URI endpointUri() {
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(PORT));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
