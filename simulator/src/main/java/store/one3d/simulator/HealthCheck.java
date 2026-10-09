package store.one3d.simulator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Container health check: exits 0 if GET /health answers 200. The runtime image has no curl. */
public final class HealthCheck {

    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault("SIMULATOR_PORT", "8090");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2))
                .build();
        int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        System.exit(status == 200 ? 0 : 1);
    }
}
