package store.one3d.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in person making the current request. Ids come from the
 * server-side session, never from request data, so a client cannot act as
 * someone else by sending another customer's id.
 */
@Component
public class CurrentUser {

    /** Who is calling: the local user id plus what the provider said about them. */
    public record Identity(UUID id, String issuer, String subject, String email, List<String> roles) {

        public boolean isOperator() {
            return roles.contains(Roles.OPERATOR);
        }
    }

    private final UserDirectory users;

    CurrentUser(UserDirectory users) {
        this.users = users;
    }

    /** The caller; the security configuration guarantees a signed-in OIDC user on these routes. */
    public Identity require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            throw new IllegalStateException("No signed-in OIDC user for this request");
        }
        String issuer = user.getIdToken().getIssuer() == null ? null : user.getIdToken().getIssuer().toString();
        if (issuer == null || user.getSubject() == null) {
            throw new IllegalStateException("ID token without issuer or subject");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name.equals(Roles.CUSTOMER) || name.equals(Roles.OPERATOR))
                .sorted()
                .toList();
        UUID id = users.idFor(issuer, user.getSubject(), user.getEmail());
        return new Identity(id, issuer, user.getSubject(), user.getEmail(), roles);
    }

    public UUID id() {
        return require().id();
    }
}
