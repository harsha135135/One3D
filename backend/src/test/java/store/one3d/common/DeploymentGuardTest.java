package store.one3d.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** The aws profile refuses local and mock settings at startup. */
class DeploymentGuardTest {

    private static final String[] AWS_SETTINGS = {
            "one3d.identity.issuer=https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_example",
            "spring.security.oauth2.client.provider.oidc.authorization-uri=https://one3d.auth.ap-south-1.amazoncognito.com/oauth2/authorize",
            "spring.security.oauth2.client.provider.oidc.token-uri=https://one3d.auth.ap-south-1.amazoncognito.com/oauth2/token",
            "spring.security.oauth2.client.provider.oidc.jwk-set-uri=https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_example/.well-known/jwks.json",
            "spring.security.oauth2.client.provider.oidc.user-info-uri=https://one3d.auth.ap-south-1.amazoncognito.com/oauth2/userInfo",
            "one3d.public-url=https://demo.one3d.store",
            "one3d.storage.endpoint=", "one3d.storage.public-endpoint=",
            "one3d.storage.access-key=", "one3d.storage.secret-key=",
            "server.servlet.session.cookie.secure=true"};

    private final ApplicationContextRunner aws = new ApplicationContextRunner()
            .withUserConfiguration(DeploymentGuard.class)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("aws"));

    @Test
    void realProviderStorageAndHttpsStart() {
        aws.withPropertyValues(AWS_SETTINGS).run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void theMockIdentityProviderIsRefused() {
        aws.withPropertyValues(AWS_SETTINGS)
                .withPropertyValues("one3d.identity.issuer=http://localhost:8180/default",
                        "spring.security.oauth2.client.provider.oidc.token-uri=http://idp:8080/default/token")
                .run(failed -> assertThat(failed).getFailure()
                        .hasStackTraceContaining("one3d.identity.issuer")
                        .hasStackTraceContaining("token-uri"));
    }

    @Test
    void localStorageOrStaticKeysAreRefused() {
        aws.withPropertyValues(AWS_SETTINGS)
                .withPropertyValues("one3d.storage.endpoint=http://127.0.0.1:9090", "one3d.storage.access-key=local-dev-only")
                .run(failed -> assertThat(failed).getFailure()
                        .hasStackTraceContaining("one3d.storage.endpoint must be empty")
                        .hasStackTraceContaining("one3d.storage.access-key must be empty"));
    }

    @Test
    void plainHttpOrInsecureCookiesAreRefused() {
        aws.withPropertyValues(AWS_SETTINGS)
                .withPropertyValues("one3d.public-url=http://demo.one3d.store", "server.servlet.session.cookie.secure=false")
                .run(failed -> assertThat(failed).getFailure()
                        .hasStackTraceContaining("public HTTPS address")
                        .hasStackTraceContaining("Secure"));
    }

    @Test
    void outsideTheAwsProfileNothingIsChecked() {
        new ApplicationContextRunner().withUserConfiguration(DeploymentGuard.class)
                .withPropertyValues("one3d.identity.issuer=http://localhost:8180/default")
                .run(started -> assertThat(started).hasNotFailed());
    }
}
