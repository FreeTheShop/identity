package com.oldnew.identity.api;

import com.oldnew.identity.support.IntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inbound limits, with capacity lowered to 2 so the test is fast. Each test uses its own client IP,
 * because buckets live for the whole Spring context.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "identity.rate-limits[0].name=register",
        "identity.rate-limits[0].method=POST",
        "identity.rate-limits[0].path-prefix=/api/v1/users",
        "identity.rate-limits[0].capacity=2",
        "identity.rate-limits[0].period=1m",
        "identity.rate-limits[1].name=token",
        "identity.rate-limits[1].method=POST",
        "identity.rate-limits[1].path-prefix=/oauth2/token",
        "identity.rate-limits[1].capacity=2",
        "identity.rate-limits[1].period=1m"
})
class RateLimitIT {

    @Autowired
    WebApplicationContext context;
    @Autowired
    MongoTemplate mongo;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mongo.remove(new Query(), "users");
        mvc = MockMvcFactory.withSecurity(context);
    }

    @Test
    void registrationIsCappedPerCallerWithRetryAfter() throws Exception {
        register("10.0.0.1", "r1@example.com").andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        register("10.0.0.1", "r2@example.com").andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        MockHttpServletResponse rejected = register("10.0.0.1", "r3@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(mongo.getCollection("users").countDocuments()).as("the rejected request never reached the app").isEqualTo(2);
    }

    @Test
    void oneNoisyCallerDoesNotBlockAnother() throws Exception {
        register("10.0.0.2", "n1@example.com").andExpect(status().isCreated());
        register("10.0.0.2", "n2@example.com").andExpect(status().isCreated());
        register("10.0.0.2", "n3@example.com").andExpect(status().isTooManyRequests());

        register("10.0.0.3", "q1@example.com").andExpect(status().isCreated());
    }

    @Test
    void tokenEndpointIsCapped() throws Exception {
        token("10.0.0.4").andExpect(status().isOk());
        token("10.0.0.4").andExpect(status().isOk());
        token("10.0.0.4").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private ResultActions register(String ip, String email) throws Exception {
        return mvc.perform(post("/api/v1/users").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\"}"));
    }

    private ResultActions token(String ip) throws Exception {
        return mvc.perform(post("/oauth2/token").with(from(ip))
                .with(httpBasic("scope-probe", "test-probe-secret"))   // the Customer Identity has no service clients
                .param("grant_type", "client_credentials")
                .param("scope", "client-core:read"));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
