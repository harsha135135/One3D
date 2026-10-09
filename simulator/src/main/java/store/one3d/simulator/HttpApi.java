package store.one3d.simulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import store.one3d.simulator.Model.FailRequest;
import store.one3d.simulator.Model.FaultRequest;
import store.one3d.simulator.Model.StartCommand;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The simulator's HTTP interface. Requests are handled one at a time.
 *
 * <pre>
 * POST /commands/start                      start an attempt (journaled before the reply)
 * GET  /commands/{commandId}                outcome and execution phase, 404 if never received
 * GET  /printers/{printerId}                printer state
 * GET  /health
 * POST /dev/printers/{printerId}/complete   (dev controls only)
 * POST /dev/printers/{printerId}/fail       {"reason": "..."}
 * POST /dev/printers/{printerId}/faults     {"dropNextStartAck": true}
 * </pre>
 */
final class HttpApi implements AutoCloseable {

    private static final Logger log = System.getLogger(HttpApi.class.getName());

    private final PrinterSimulator simulator;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    HttpApi(PrinterSimulator simulator, String bindAddress, int port, boolean devControls) throws IOException {
        this.simulator = simulator;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/health", exchange -> handle(exchange, "GET", () -> Map.of("status", "UP", "simulated", true)));
        server.createContext("/commands/", this::commands);
        server.createContext("/printers/", exchange -> handle(exchange, "GET", () -> simulator
                .findPrinter(lastSegment(exchange))
                .orElseThrow(() -> notFound("PRINTER_NOT_FOUND", "Printer " + lastSegment(exchange) + " is unknown."))));
        if (devControls) {
            server.createContext("/dev/printers/", this::devControls);
        }
        server.setExecutor(executor);
    }

    void start() {
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void commands(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/commands/start")) {
            startCommand(exchange);
        } else {
            handle(exchange, "GET", () -> simulator.findCommand(lastSegment(exchange))
                    .orElseThrow(() -> notFound("COMMAND_NOT_FOUND", "Command " + lastSegment(exchange) + " was never received.")));
        }
    }

    private void startCommand(HttpExchange exchange) throws IOException {
        try {
            requireMethod(exchange, "POST");
            PrinterSimulator.StartDecision decision = simulator.start(read(exchange, StartCommand.class));
            if (decision.dropAcknowledgement()) {
                // Fault injection: the command is already journaled; the caller never hears back.
                log.log(Level.INFO, "Dropping acknowledgement for command {0}", decision.result().commandId());
                exchange.close();
                return;
            }
            send(exchange, 200, decision.result());
        } catch (SimulatorException e) {
            sendError(exchange, e);
        } catch (RuntimeException e) {
            sendUnexpected(exchange, e);
        }
    }

    private void devControls(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
        // /dev/printers/{printerId}/{action}
        if (parts.length != 5) {
            sendError(exchange, notFound("NOT_FOUND", "Unknown path."));
            return;
        }
        String printerId = parts[3];
        String action = parts[4];
        switch (action) {
            case "complete" -> handle(exchange, "POST", () -> simulator.complete(printerId));
            case "fail" -> handle(exchange, "POST", () -> simulator.fail(printerId, read(exchange, FailRequest.class).reason()));
            case "faults" -> handle(exchange, "POST", () -> {
                simulator.dropNextStartAcknowledgement(printerId, read(exchange, FaultRequest.class).dropNextStartAck());
                return Map.of("printerId", printerId);
            });
            default -> sendError(exchange, notFound("NOT_FOUND", "Unknown action " + action + "."));
        }
    }

    @FunctionalInterface
    private interface Action {
        Object run();
    }

    private void handle(HttpExchange exchange, String method, Action action) throws IOException {
        try {
            requireMethod(exchange, method);
            send(exchange, 200, action.run());
        } catch (SimulatorException e) {
            sendError(exchange, e);
        } catch (RuntimeException e) {
            sendUnexpected(exchange, e);
        }
    }

    private <T> T read(HttpExchange exchange, Class<T> type) {
        try {
            return json.readValue(exchange.getRequestBody(), type);
        } catch (JacksonException e) {
            throw new SimulatorException(400, "INVALID_REQUEST", "The request body is not valid JSON for this endpoint.");
        }
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!exchange.getRequestMethod().equals(method)) {
            throw new SimulatorException(405, "METHOD_NOT_ALLOWED", "Use " + method + ".");
        }
    }

    private static String lastSegment(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static SimulatorException notFound(String code, String message) {
        return new SimulatorException(404, code, message);
    }

    private void sendError(HttpExchange exchange, SimulatorException e) throws IOException {
        send(exchange, e.status(), Map.of("code", e.code(), "message", e.getMessage()));
    }

    private void sendUnexpected(HttpExchange exchange, RuntimeException e) throws IOException {
        log.log(Level.ERROR, "Unexpected simulator error", e);
        send(exchange, 500, Map.of("code", "INTERNAL_ERROR", "message", "Unexpected simulator error."));
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
