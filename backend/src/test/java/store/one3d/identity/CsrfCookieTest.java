package store.one3d.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.TestUsers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The browser receives the CSRF token as a readable XSRF-TOKEN cookie on its
 * first request. Web layer only, with the real security configuration and a
 * fresh context: Spring Security's test csrf() helper, used by the integration
 * tests, replaces the token repository of the shared filter and would hide
 * the real cookie behaviour.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, SecurityProblems.class})
class CsrfCookieTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CurrentUser currentUser;

    @Test
    void theFirstResponseCarriesAReadableCsrfCookie() throws Exception {
        when(currentUser.require()).thenReturn(new CurrentUser.Identity(UUID.randomUUID(), TestUsers.ISSUER,
                "someone", "someone@example.test", List.of(Roles.CUSTOMER)));

        Cookie token = mvc.perform(get("/api/me").with(TestUsers.customer("someone")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        assertThat(token.isHttpOnly()).isFalse(); // the frontend must be able to read it
    }

    @Test
    void anUnauthenticatedApiCallGetsAJson401NotARedirect() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
