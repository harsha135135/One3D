package store.one3d.production;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HTTP success alone is not evidence that this command was accepted or refused. */
class SimulatorClientTest {

    private final UUID commandId = UUID.randomUUID();
    private volatile String response;
    private HttpServer server;
    private SimulatorClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new SimulatorClient(() -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                JsonMapper.builder().build(), Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"commandId\":\"COMMAND\"}",
            "{\"commandId\":\"COMMAND\",\"outcome\":\"NEW_OUTCOME\"}",
            "{\"commandId\":\"00000000-0000-0000-0000-000000000000\",\"outcome\":\"REJECTED_BUSY\"}"})
    void anInvalidStartReportDoesNotEstablishARefusal(String body) {
        response = body.replace("COMMAND", commandId.toString());

        assertThatThrownBy(() -> client.start(startRequest()))
                .isInstanceOf(SimulatorUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"commandId\":\"COMMAND\"}",
            "{\"commandId\":\"COMMAND\",\"outcome\":\"NEW_OUTCOME\"}",
            "{\"commandId\":\"COMMAND\",\"outcome\":\"STARTED\"}",
            "{\"commandId\":\"COMMAND\",\"outcome\":\"STARTED\",\"phase\":\"UNKNOWN\"}",
            "{\"commandId\":\"COMMAND\",\"outcome\":\"REJECTED_BUSY\",\"phase\":\"COMPLETED\"}",
            "{\"commandId\":\"00000000-0000-0000-0000-000000000000\",\"outcome\":\"STARTED\",\"phase\":\"COMPLETED\"}"})
    void anInvalidJournalReportCannotChangeTheAttempt(String body) {
        response = body.replace("COMMAND", commandId.toString());

        assertThatThrownBy(() -> client.findCommand(commandId))
                .isInstanceOf(SimulatorUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "COMPLETED", "FAILED"})
    void aJournalReportNamesThisCommandAndAKnownExecutionPhase(String phase) throws Exception {
        response = """
                {"commandId":"%s","outcome":"STARTED","phase":"%s"}
                """.formatted(commandId, phase);

        assertThat(client.findCommand(commandId)).get()
                .satisfies(report -> assertThat(report.phase()).isEqualTo(phase));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED_BUSY", "REJECTED_BED_NOT_CLEAR"})
    void theKnownRefusalsRemainUsable(String outcome) throws Exception {
        response = """
                {"commandId":"%s","outcome":"%s"}
                """.formatted(commandId, outcome);

        assertThat(client.start(startRequest()).outcome()).isEqualTo(outcome);
        assertThat(client.findCommand(commandId)).get()
                .satisfies(report -> assertThat(report.outcome()).isEqualTo(outcome));
    }

    @Test
    void aStartAcknowledgementDoesNotNeedTheJournalOnlyPhaseField() throws Exception {
        response = """
                {"commandId":"%s","outcome":"STARTED"}
                """.formatted(commandId);

        assertThat(client.start(startRequest()).phase()).isEqualTo("RUNNING");
    }

    private SimulatorClient.StartRequest startRequest() {
        return new SimulatorClient.StartRequest(commandId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "digest", null);
    }
}
