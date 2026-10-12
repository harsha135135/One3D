package store.one3d.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Who may call what. Authentication is OIDC login with a server-side session
 * (cookie); the backend checks roles here and record ownership in the services.
 *
 * <ul>
 *   <li>{@code GET /api/catalog/**}: anyone.</li>
 *   <li>{@code /api/ops/**}: OPERATOR only. "/ops" in the UI is just a page group.</li>
 *   <li>{@code POST /api/payments/razorpay/webhook}: no session and no CSRF
 *       token; it is authenticated by Razorpay's signature instead. This is
 *       the only exception, for that one method and path.</li>
 *   <li>Any other {@code /api/**}: any signed-in user (CUSTOMER).</li>
 * </ul>
 *
 * Every state-changing request needs the CSRF token: the browser reads the
 * {@code XSRF-TOKEN} cookie and sends it back as the {@code X-XSRF-TOKEN} header.
 */
@Configuration
class SecurityConfig {

    /** Exactly one method and path; signed by Razorpay (see payments.RazorpayWebhookController). */
    private static final RequestMatcher RAZORPAY_WEBHOOK =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/payments/razorpay/webhook");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblems problems,
                                            @Value("${one3d.public-url}") String publicUrl) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers(RAZORPAY_WEBHOOK).permitAll()
                        .requestMatchers("/api/ops/**").hasRole("OPERATOR")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2Login(login -> login.defaultSuccessUrl(publicUrl + "/", true))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(RAZORPAY_WEBHOOK))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(problems,
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /**
     * Validates ID tokens: signature (via the provider's JWK set), expiry, the
     * standard OIDC checks, and that the issuer is exactly the configured one.
     * Endpoints are configured explicitly (no discovery call at startup), so the
     * issuer check is added here.
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(@Value("${one3d.identity.issuer}") String issuer) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(registration -> idTokenValidator(registration, issuer));
        return factory;
    }

    static OAuth2TokenValidator<Jwt> idTokenValidator(ClientRegistration registration, String issuer) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new OidcIdTokenValidator(registration),
                new JwtIssuerValidator(issuer));
    }
}
