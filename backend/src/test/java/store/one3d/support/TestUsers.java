package store.one3d.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import store.one3d.identity.Roles;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

/**
 * Signed-in test users, as an OIDC login would produce them. Roles are given
 * directly here; the groups-to-roles mapping has its own unit test.
 */
public final class TestUsers {

    public static final String ISSUER = "https://idp.test";

    /** Synthetic owner of orders created before identity existed (migration V6). */
    public static final java.util.UUID LEGACY_CUSTOMER_ID =
            java.util.UUID.fromString("0d3d0000-0000-4000-8000-000000000201");

    private TestUsers() {
    }

    public static OidcLoginRequestPostProcessor customer(String subject) {
        return login(subject, new SimpleGrantedAuthority(Roles.CUSTOMER));
    }

    /** An operator, who can also act as a customer. The default user of integration tests. */
    public static OidcLoginRequestPostProcessor operator(String subject) {
        return login(subject, new SimpleGrantedAuthority(Roles.CUSTOMER), new SimpleGrantedAuthority(Roles.OPERATOR));
    }

    private static OidcLoginRequestPostProcessor login(String subject, SimpleGrantedAuthority... roles) {
        return oidcLogin()
                .idToken(token -> token.issuer(ISSUER).subject(subject).claim("email", subject + "@example.test"))
                .authorities(roles);
    }
}
