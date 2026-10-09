package store.one3d.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Every MockMvc request in an integration test is sent by a signed-in operator
 * (who also has the customer role) with a valid CSRF token, unless the test
 * adds its own user ({@code .with(TestUsers.customer("a"))}, {@code .with(anonymous())}).
 * Security itself is tested in SecurityIT, which overrides these defaults.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SignedInByDefault {

    public static final String DEFAULT_SUBJECT = "test-operator";

    @Bean
    MockMvcBuilderCustomizer signedInOperatorWithCsrf() {
        return builder -> builder.defaultRequest(get("/").with(TestUsers.operator(DEFAULT_SUBJECT)).with(csrf()));
    }
}
