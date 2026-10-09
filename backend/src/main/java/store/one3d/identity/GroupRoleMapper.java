package store.one3d.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the identity provider's groups claim into roles at sign-in. Everyone
 * who signs in is a CUSTOMER; members of the operator group are also
 * OPERATORs. Group membership is managed in the provider (Cognito groups on
 * AWS), so role assignment is restricted to whoever administers it.
 */
@Component
class GroupRoleMapper implements GrantedAuthoritiesMapper {

    private final String groupsClaim;
    private final String operatorGroup;

    GroupRoleMapper(@Value("${one3d.identity.groups-claim}") String groupsClaim,
                    @Value("${one3d.identity.operator-group}") String operatorGroup) {
        this.groupsClaim = groupsClaim;
        this.operatorGroup = operatorGroup;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        mapped.add(new SimpleGrantedAuthority(Roles.CUSTOMER));
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc && groupsOf(oidc).contains(operatorGroup)) {
                mapped.add(new SimpleGrantedAuthority(Roles.OPERATOR));
            }
        }
        return mapped;
    }

    private List<String> groupsOf(OidcUserAuthority authority) {
        Object groups = authority.getIdToken().getClaims().get(groupsClaim);
        if (groups instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
