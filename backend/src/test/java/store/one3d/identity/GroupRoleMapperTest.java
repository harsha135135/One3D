package store.one3d.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The provider's groups claim decides who is an operator; everyone signed in is a customer. */
class GroupRoleMapperTest {

    @Test
    void membersOfTheOperatorGroupBecomeOperators() {
        GroupRoleMapper mapper = new GroupRoleMapper("cognito:groups", "operators");

        assertThat(roles(mapper, Map.of("cognito:groups", List.of("operators", "staff"))))
                .contains(Roles.CUSTOMER, Roles.OPERATOR);
    }

    @Test
    void everyoneElseIsOnlyACustomer() {
        GroupRoleMapper mapper = new GroupRoleMapper("groups", "operators");

        assertThat(roles(mapper, Map.of("groups", List.of("customers")))).contains(Roles.CUSTOMER)
                .doesNotContain(Roles.OPERATOR);
        assertThat(roles(mapper, Map.of())).contains(Roles.CUSTOMER).doesNotContain(Roles.OPERATOR);
    }

    @Test
    void theGroupsClaimNameIsConfigurable() {
        GroupRoleMapper mapper = new GroupRoleMapper("groups", "operators");

        // A claim with a different name is ignored.
        assertThat(roles(mapper, Map.of("cognito:groups", List.of("operators")))).doesNotContain(Roles.OPERATOR);
    }

    private static List<String> roles(GroupRoleMapper mapper, Map<String, Object> claims) {
        OidcIdToken.Builder token = OidcIdToken.withTokenValue("token")
                .subject("someone").issuer("https://idp.test")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(token::claim);
        List<GrantedAuthority> input = List.of(new OidcUserAuthority(token.build()));
        return mapper.mapAuthorities(input).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
