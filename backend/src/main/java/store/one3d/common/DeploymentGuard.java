package store.one3d.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Under the {@code aws} profile (the deployed demo), refuses to start with
 * anything meant only for local development: the mock identity provider or a
 * non-HTTPS issuer, a public address that is not HTTPS, local storage or
 * static storage keys, or insecure session cookies. The demo payment shortcut
 * is refused separately (payments.PaymentModeGuard).
 */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
class DeploymentGuard {

    private static final List<String> LOCAL_MARKERS = List.of("localhost", "127.0.0.1", "://idp", "mock", "0.0.0.0");

    DeploymentGuard(Environment environment) {
        List<String> problems = new ArrayList<>();
        String issuer = value(environment, "one3d.identity.issuer");
        if (!issuer.startsWith("https://") || looksLocal(issuer)) {
            problems.add("one3d.identity.issuer must be the HTTPS issuer of the real identity provider");
        }
        for (String uri : List.of("authorization-uri", "token-uri", "jwk-set-uri", "user-info-uri")) {
            String endpoint = value(environment, "spring.security.oauth2.client.provider.oidc." + uri);
            if (!endpoint.startsWith("https://") || looksLocal(endpoint)) {
                problems.add("the identity provider's " + uri + " must be an HTTPS address of the real provider");
            }
        }
        String publicUrl = value(environment, "one3d.public-url");
        if (!publicUrl.startsWith("https://") || looksLocal(publicUrl)) {
            problems.add("one3d.public-url must be the public HTTPS address");
        }
        for (String key : List.of("one3d.storage.endpoint", "one3d.storage.public-endpoint",
                "one3d.storage.access-key", "one3d.storage.secret-key")) {
            if (!value(environment, key).isBlank()) {
                problems.add(key + " must be empty on AWS (real S3 with the instance role)");
            }
        }
        if (!"true".equals(value(environment, "server.servlet.session.cookie.secure"))) {
            problems.add("the session cookie must be Secure");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start the aws profile: " + String.join("; ", problems));
        }
    }

    private static String value(Environment environment, String key) {
        String value = environment.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private static boolean looksLocal(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return LOCAL_MARKERS.stream().anyMatch(lower::contains);
    }
}
