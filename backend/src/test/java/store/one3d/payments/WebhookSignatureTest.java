package store.one3d.payments;

import org.junit.jupiter.api.Test;
import store.one3d.support.RazorpayEvents;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The signature check, against signatures computed independently in RazorpayEvents. */
class WebhookSignatureTest {

    private static final String SECRET = "whsec-for-unit-test";
    private static final String BODY = "{\"event\":\"payment.captured\",\"payload\":{}}";

    @Test
    void aSignatureOverTheExactBodyWithTheSecretMatches() {
        String signature = RazorpayEvents.sign(BODY, SECRET);

        assertThat(WebhookSignature.matches(bytes(BODY), signature, SECRET)).isTrue();
        assertThat(WebhookSignature.matches(bytes(BODY), signature.toUpperCase(), SECRET)).isTrue();
    }

    @Test
    void anyChangeToTheBodyTheSecretOrTheSignatureFails() {
        String signature = RazorpayEvents.sign(BODY, SECRET);

        assertThat(WebhookSignature.matches(bytes(BODY + " "), signature, SECRET)).isFalse();
        assertThat(WebhookSignature.matches(bytes(BODY), signature, SECRET + "x")).isFalse();
        assertThat(WebhookSignature.matches(bytes(BODY), signature.substring(1), SECRET)).isFalse();
        assertThat(WebhookSignature.matches(bytes(BODY), "", SECRET)).isFalse();
        assertThat(WebhookSignature.matches(bytes(BODY), null, SECRET)).isFalse();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
