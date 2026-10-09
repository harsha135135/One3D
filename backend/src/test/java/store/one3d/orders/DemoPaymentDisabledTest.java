package store.one3d.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import store.one3d.support.TestUsers;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without the "demo" profile the simulated-payment shortcut does not exist,
 * so it cannot confirm an order by accident. Web layer only; no database.
 */
@WebMvcTest(DemoPaymentController.class)
class DemoPaymentDisabledTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orderService;

    @Test
    void simulatedPaymentEndpointIsAbsentByDefault() throws Exception {
        // Signed in and with a CSRF token, so only the missing endpoint can explain a 404.
        mvc.perform(post("/api/demo/orders/{id}/simulate-payment", UUID.randomUUID())
                        .with(TestUsers.customer("someone"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(orderService);
    }
}
