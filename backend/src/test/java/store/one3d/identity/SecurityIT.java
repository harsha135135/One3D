package store.one3d.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import store.one3d.support.PostgresIntegrationTest;
import store.one3d.support.RazorpayEvents;
import store.one3d.support.TestUsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication, roles, record ownership and CSRF, checked through the real
 * security filter chain. Each request names its user explicitly, overriding
 * the integration tests' default signed-in operator.
 */
@PostgresIntegrationTest
class SecurityIT {

    private static final String DEMO_CONFIGURATION = "0d3d0000-0000-4000-8000-000000000002";
    private static final String WEBHOOK = "/api/payments/razorpay/webhook";

    @Autowired
    MockMvc mvc;

    @Autowired
    WebApplicationContext context;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private String customerA;
    private String customerB;

    @BeforeEach
    void users() {
        customerA = "customer-a-" + UUID.randomUUID();
        customerB = "customer-b-" + UUID.randomUUID();
    }

    // --- every route, by role ------------------------------------------------

    /**
     * Every /api route the application has, read from Spring itself, so a route
     * added later cannot be left open by mistake.
     */
    @Test
    void everyApiRouteNeedsSignInExceptTheCatalogAndTheSignedWebhook() throws Exception {
        List<String> checked = new ArrayList<>();
        for (Route route : apiRoutes()) {
            MvcResult result = mvc.perform(route.toRequest().with(anonymous())).andReturn();
            boolean publicRoute = route.method() == HttpMethod.GET && route.pattern().startsWith("/api/catalog/");
            boolean webhook = route.method() == HttpMethod.POST && route.pattern().equals(WEBHOOK);
            if (publicRoute) {
                assertThat(result.getResponse().getStatus()).as(route.toString()).isNotEqualTo(401);
            } else if (webhook) {
                // Open without a session, but only with Razorpay's signature.
                assertThat(result.getResponse().getStatus()).as(route.toString()).isEqualTo(400);
                assertThat(result.getResponse().getContentAsString()).contains("INVALID_SIGNATURE");
            } else {
                assertThat(result.getResponse().getStatus()).as(route.toString()).isEqualTo(401);
                assertThat(result.getResponse().getContentAsString()).as(route.toString()).contains("UNAUTHENTICATED");
            }
            checked.add(route.toString());
        }
        assertThat(checked).as("routes checked").hasSizeGreaterThan(15);
    }

    @Test
    void everyOperatorRouteIsForbiddenToCustomers() throws Exception {
        List<Route> operatorRoutes = apiRoutes().stream().filter(r -> r.pattern().startsWith("/api/ops/")).toList();
        assertThat(operatorRoutes).hasSizeGreaterThan(8);
        for (Route route : operatorRoutes) {
            MvcResult result = mvc.perform(route.toRequest().with(TestUsers.customer(customerA))).andReturn();
            assertThat(result.getResponse().getStatus()).as(route.toString()).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString()).as(route.toString()).contains("ACCESS_DENIED");
        }
    }

    @Test
    void operatorsCanUseTheOperatorRoutes() throws Exception {
        mvc.perform(get("/api/ops/orders").with(TestUsers.operator("operator-" + UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ops/printers").with(TestUsers.operator("operator-" + UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    // --- ownership -----------------------------------------------------------

    @Test
    void aCustomerCannotReadListOrPayForAnotherCustomersOrder() throws Exception {
        String orderOfA = placeOrderAs(customerA);

        mvc.perform(get("/api/orders/" + orderOfA).with(TestUsers.customer(customerA)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + orderOfA).with(TestUsers.customer(customerB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(get("/api/orders").with(TestUsers.customer(customerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + orderOfA + "')]").isEmpty());
        mvc.perform(post("/api/orders/" + orderOfA + "/checkout").with(TestUsers.customer(customerB)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + orderOfA + "/payment-check").with(TestUsers.customer(customerB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + orderOfA).with(TestUsers.customer(customerA)))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
    }

    @Test
    void aCustomerSeesOnlyTheirOwnOrdersNewestFirst() throws Exception {
        String first = placeOrderAs(customerA);
        String second = placeOrderAs(customerA);
        placeOrderAs(customerB);

        mvc.perform(get("/api/orders?size=10").with(TestUsers.customer(customerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[1].id").value(first));
    }

    @Test
    void theOwnerIsTakenFromTheSessionNotFromTheRequest() throws Exception {
        String body = mvc.perform(post("/api/orders").with(TestUsers.customer(customerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productConfigurationId": "%s", "quantity": 1,
                                 "customerId": "%s"}
                                """.formatted(DEMO_CONFIGURATION, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/orders/" + orderId).with(TestUsers.customer(customerA))).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(TestUsers.customer(customerA)))
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(body, "$.customerId")));
    }

    @Test
    void operatorsCanReadAnyOrder() throws Exception {
        String orderOfA = placeOrderAs(customerA);

        mvc.perform(get("/api/ops/orders/" + orderOfA).with(TestUsers.operator("operator-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(orderOfA));
    }

    @Test
    void meDescribesTheSignedInUserAndTheirRoles() throws Exception {
        mvc.perform(get("/api/me").with(TestUsers.customer(customerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customerA + "@example.test"))
                .andExpect(jsonPath("$.roles").value(contains("CUSTOMER")));
        mvc.perform(get("/api/me").with(TestUsers.operator("operator-x")))
                .andExpect(jsonPath("$.roles").value(containsInAnyOrder("CUSTOMER", "OPERATOR")));
    }

    // --- CSRF ----------------------------------------------------------------

    @Test
    void aStateChangingRequestWithoutTheCsrfTokenIsRefused() throws Exception {
        MockMvc bare = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        bare.perform(placeOrder().with(TestUsers.customer(customerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        bare.perform(placeOrder().with(TestUsers.customer(customerA)).with(csrf()))
                .andExpect(status().isCreated());
    }

    /**
     * Only POST to the webhook skips the session and the CSRF token, and it
     * still needs a valid signature. Other methods on that path are not open.
     */
    @Test
    void onlyTheSignedWebhookIsExemptFromSessionAndCsrf() throws Exception {
        MockMvc bare = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String failed = RazorpayEvents.event("payment.failed", RazorpayEvents.newPaymentId(), "order_unknown", 100,
                "INR", "failed", java.time.Instant.now());

        bare.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        bare.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(failed)
                        .header("X-Razorpay-Event-Id", RazorpayEvents.newEventId())
                        .header("X-Razorpay-Signature", RazorpayEvents.sign(failed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
        bare.perform(request(HttpMethod.PUT, WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        bare.perform(request(HttpMethod.PUT, WEBHOOK).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isUnauthorized());
        bare.perform(get(WEBHOOK)).andExpect(status().isUnauthorized());
        // Every other mutation still needs the CSRF token (see the test above).
    }

    // --- helpers -------------------------------------------------------------

    private String placeOrderAs(String subject) throws Exception {
        String body = mvc.perform(placeOrder().with(TestUsers.customer(subject)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static MockHttpServletRequestBuilder placeOrder() {
        return post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productConfigurationId\": \"" + DEMO_CONFIGURATION + "\", \"quantity\": 1}");
    }

    private record Route(HttpMethod method, String pattern) {

        MockHttpServletRequestBuilder toRequest() {
            String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            return request(method, path);
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    /** All /api routes registered with Spring MVC. */
    private List<Route> apiRoutes() {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                if (methods.isEmpty()) {
                    routes.add(new Route(HttpMethod.GET, pattern));
                }
                for (RequestMethod method : methods) {
                    routes.add(new Route(HttpMethod.valueOf(method.name()), pattern));
                }
            }
        }
        return routes;
    }
}
