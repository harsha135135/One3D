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
    void everyApiRouteNeedsSignInExceptTheCatalog() throws Exception {
        List<String> checked = new ArrayList<>();
        for (Route route : apiRoutes()) {
            MvcResult result = mvc.perform(route.toRequest().with(anonymous())).andReturn();
            boolean publicRoute = route.method() == HttpMethod.GET && route.pattern().startsWith("/api/catalog/");
            if (publicRoute) {
                assertThat(result.getResponse().getStatus()).as(route.toString()).isNotEqualTo(401);
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
        mvc.perform(post("/api/demo/orders/" + orderOfA + "/simulate-payment").with(TestUsers.customer(customerB)))
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
