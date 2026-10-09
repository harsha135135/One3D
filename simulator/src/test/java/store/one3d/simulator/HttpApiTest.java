package store.one3d.simulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The HTTP interface, including a lost acknowledgement and a restart on the same journal. */
class HttpApiTest {

    @TempDir
    Path dir;

    private Journal journal;
    private PrinterSimulator simulator;
    private HttpApi api;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String printer = "printer-" + UUID.randomUUID();

    @AfterEach
    void stop() {
        if (api != null) {
            api.close();
        }
        if (journal != null) {
            journal.close();
        }
    }

    @Test
    void startsAnAttemptAndReportsItsCommand() throws Exception {
        boot(true);

        HttpResponse<String> started = post("/commands/start", startBody("command-1", "attempt-1"));
        HttpResponse<String> command = get("/commands/command-1");

        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(started.body()).contains("\"outcome\":\"STARTED\"");
        assertThat(command.body()).contains("\"phase\":\"RUNNING\"").contains("\"simulated\":true");
    }

    @Test
    void aLostAcknowledgementFailsTheCallerQuicklyButTheCommandIsJournaled() throws Exception {
        boot(true);
        post("/dev/printers/" + printer + "/faults", "{\"dropNextStartAck\": true}");

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> post("/commands/start", startBody("command-1", "attempt-1")))
                .isInstanceOf(IOException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));

        // The simulator did start: asking by command id reveals it, and resending is harmless.
        assertThat(get("/commands/command-1").body()).contains("\"outcome\":\"STARTED\"");
        assertThat(post("/commands/start", startBody("command-1", "attempt-1")).body())
                .contains("\"outcome\":\"STARTED\"");
        assertThat(simulator.executionCount("attempt-1")).isEqualTo(1);
    }

    @Test
    void aCommandThatNeverArrivedIsNotFound() throws Exception {
        boot(true);

        HttpResponse<String> response = get("/commands/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("COMMAND_NOT_FOUND");
    }

    @Test
    void devControlsDoNotExistUnlessEnabled() throws Exception {
        boot(false);

        assertThat(post("/dev/printers/" + printer + "/complete", "").statusCode()).isEqualTo(404);
    }

    @Test
    void aRestartedProcessAnswersFromTheSameJournal() throws Exception {
        boot(true);
        post("/commands/start", startBody("command-1", "attempt-1"));
        api.close();
        journal.close();

        boot(true);

        assertThat(get("/commands/command-1").body()).contains("\"phase\":\"RUNNING\"");
        assertThat(get("/printers/" + printer).body()).contains("\"runningAttemptId\":\"attempt-1\"");
    }

    private void boot(boolean devControls) throws IOException {
        journal = new Journal(dir.resolve("journal.db"));
        simulator = new PrinterSimulator(journal, Clock.systemUTC());
        api = new HttpApi(simulator, "127.0.0.1", 0, devControls);
        api.start();
    }

    private String startBody(String commandId, String attemptId) {
        return """
                {"commandId": "%s", "printerId": "%s", "attemptId": "%s", "jobId": "job-1",
                 "payloadHash": "hash-%s", "clearedAttemptId": null}
                """.formatted(commandId, printer, attemptId, attemptId);
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + api.port() + path);
    }
}
