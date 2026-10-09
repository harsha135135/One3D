package store.one3d.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ID tokens from any issuer other than the configured one are rejected. */
class IdTokenIssuerTest {

    private static final String ISSUER = "https://idp.example";

    private final ClientRegistration registration = ClientRegistration.withRegistrationId("oidc")
            .clientId("client").clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://app.example/login/oauth2/code/oidc")
            .authorizationUri(ISSUER + "/authorize").tokenUri(ISSUER + "/token")
            .jwkSetUri(ISSUER + "/jwks").scope("openid")
            .build();

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.idTokenValidator(registration, ISSUER);

    @Test
    void aTokenFromTheConfiguredIssuerIsValid() {
        assertThat(validator.validate(token(ISSUER)).hasErrors()).isFalse();
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        assertThat(validator.validate(token("https://attacker.example")).hasErrors()).isTrue();
    }

    private static Jwt token(String issuer) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(issuer).subject("someone").audience(List.of("client"))
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
    }
}
