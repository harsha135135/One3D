package store.one3d.production;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for the printer simulator, the only external boundary of the
 * production module. Never called inside a database transaction.
 */
@Component
class SimulatorClient {

    /** The start command sent to the simulator. */
    record StartRequest(UUID commandId, UUID printerId, UUID attemptId, UUID jobId, String payloadHash,
                        UUID clearedAttemptId) {
    }

    /**
     * What the simulator's journal says about a command.
     *
     * @param outcome STARTED, REJECTED_BUSY or REJECTED_BED_NOT_CLEAR
     * @param phase   RUNNING, COMPLETED or FAILED when started, otherwise null
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CommandReport(UUID commandId, String outcome, String phase, String failureReason) {

        boolean started() {
            return "STARTED".equals(outcome);
        }
    }

    private final SimulatorEndpoint endpoint;
    private final JsonMapper json;
    private final HttpClient http;
    private final Duration requestTimeout;

    SimulatorClient(SimulatorEndpoint endpoint, JsonMapper json,
                    @Value("${one3d.simulator.connect-timeout}") Duration connectTimeout,
                    @Value("${one3d.simulator.request-timeout}") Duration requestTimeout) {
        this.endpoint = endpoint;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    /** Asks the journal about a command. Empty means the simulator never received it. */
    Optional<CommandReport> findCommand(UUID commandId) throws SimulatorUnavailableException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/commands/" + commandId)).GET());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(parse(expectOk(response), CommandReport.class));
    }

    /** Sends a start command. The simulator journals its decision before answering. */
    CommandReport start(StartRequest request) throws SimulatorUnavailableException {
        String body;
        try {
            body = json.writeValueAsString(request);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize start command", e);
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/commands/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        CommandReport report = parse(expectOk(response), CommandReport.class);
        return new CommandReport(report.commandId(), report.outcome(), report.started() ? "RUNNING" : null, null);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws SimulatorUnavailableException {
        try {
            return http.send(request.timeout(requestTimeout).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Some failures (an unknown host, a refused connection) carry no message; name the kind instead.
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new SimulatorUnavailableException("Simulator did not answer: " + detail, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimulatorUnavailableException("Interrupted while calling the simulator", e);
        }
    }

    private static String expectOk(HttpResponse<String> response) throws SimulatorUnavailableException {
        if (response.statusCode() != 200) {
            throw new SimulatorUnavailableException(
                    "Simulator answered " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private <T> T parse(String body, Class<T> type) throws SimulatorUnavailableException {
        try {
            return json.readValue(body, type);
        } catch (JacksonException e) {
            throw new SimulatorUnavailableException("Unreadable simulator response", e);
        }
    }

    private URI uri(String path) {
        return endpoint.baseUri().resolve(path);
    }
}
