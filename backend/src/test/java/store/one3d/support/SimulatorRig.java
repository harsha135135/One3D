package store.one3d.support;

import org.springframework.beans.factory.DisposableBean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import store.one3d.production.SimulatorEndpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

/**
 * The real printer simulator for integration tests: the image built from
 * {@code simulator/Dockerfile}, running as a separate container with its SQLite
 * journal in a temporary host directory. It is also the backend's
 * {@link SimulatorEndpoint}, so a restart on a new port, or a deliberately
 * unreachable address, is seen by the backend immediately.
 */
public final class SimulatorRig implements SimulatorEndpoint, DisposableBean {

    private static final int PORT = 8090;
    private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile("one3d-simulator-test", false)
            .withDockerfile(Path.of("../simulator/Dockerfile"));
    /** Nothing listens here: calls fail at once with "connection refused". */
    private static final URI UNREACHABLE = URI.create("http://127.0.0.1:9");

    private final Path journalDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private GenericContainer<?> container;
    private boolean unreachable;

    public SimulatorRig() {
        try {
            journalDir = Files.createTempDirectory("one3d-simulator-journal");
            // The container runs as a non-root user with a different uid.
            Files.setPosixFilePermissions(journalDir, PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        container = newContainer();
        container.start();
    }

    @Override
    public URI baseUri() {
        return unreachable ? UNREACHABLE : liveUri();
    }

    /** Stops the simulator process and starts a new one on the same journal (new port). */
    public void restart() {
        container.stop();
        container = newContainer();
        container.start();
    }

    /** Makes the backend's calls fail as if the simulator were down, without stopping it. */
    public void setUnreachable(boolean unreachable) {
        this.unreachable = unreachable;
    }

    /** Ends the printer's running execution successfully (simulator dev control). */
    public String complete(String printerId) {
        return post("/dev/printers/" + printerId + "/complete", "");
    }

    public String fail(String printerId, String reason) {
        return post("/dev/printers/" + printerId + "/fail", "{\"reason\": \"" + reason + "\"}");
    }

    /** The simulator will journal the next start command for this printer and then drop the reply. */
    public void dropNextStartAcknowledgement(String printerId) {
        post("/dev/printers/" + printerId + "/faults", "{\"dropNextStartAck\": true}");
    }

    /** Sends a start command directly, bypassing the backend (to set up conflicting printer states). */
    public String sendStart(String commandId, String printerId, String attemptId, String clearedAttemptId) {
        return post("/commands/start", """
                {"commandId": "%s", "printerId": "%s", "attemptId": "%s", "jobId": "external-job",
                 "payloadHash": "hash-%s", "clearedAttemptId": %s}
                """.formatted(commandId, printerId, attemptId, commandId,
                clearedAttemptId == null ? "null" : "\"" + clearedAttemptId + "\""));
    }

    public String printer(String printerId) {
        return get("/printers/" + printerId);
    }

    public String command(String commandId) {
        return get("/commands/" + commandId);
    }

    @Override
    public void destroy() {
        container.stop();
    }

    private GenericContainer<?> newContainer() {
        return new GenericContainer<>(IMAGE)
                .withExposedPorts(PORT)
                .withEnv("SIMULATOR_DEV_CONTROLS", "true")
                .withFileSystemBind(journalDir.toString(), "/data", BindMode.READ_WRITE)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));
    }

    private URI liveUri() {
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(PORT));
    }

    private String post(String path, String body) {
        return send(HttpRequest.newBuilder(liveUri().resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private String get(String path) {
        return send(HttpRequest.newBuilder(liveUri().resolve(path)).GET());
    }

    private String send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() + " " + response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
