package com.oldnew.identity.api;

import com.oldnew.identity.support.IntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Customer Identity (ADR-0009): the Client UI and self sign-up, nothing for staff. */
@IntegrationTest
class CustomerInstanceIT {

    @Autowired
    WebApplicationContext context;
    @Autowired
    RegisteredClientRepository clients;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcFactory.withSecurity(context);
    }

    @Test
    void registersTheClientUiAndNoStaffOrServiceClients() {
        assertThat(clients.findByClientId("client-ui").getScopes())
                .contains("client-aggregator:read", "client-core:read", "product:read")
                .doesNotContain("admin-aggregator:read", "admin-core:read", "product:write");
        assertThat(clients.findByClientId("admin-ui")).isNull();
        assertThat(clients.findByClientId("price-service")).isNull();
        assertThat(clients.findByClientId("validation-service")).isNull();
    }

    @Test
    void clientUiReturnsToItsHomePageAfterSigningOut() {
        assertThat(clients.findByClientId("client-ui").getPostLogoutRedirectUris())
                .containsExactly("http://localhost:3001/");
    }

    /** Browsers send cookies to every port of a host, so the two instances' sessions need different names. */
    @Test
    void sessionCookieHasItsOwnName() {
        assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("OLDNEW_CUSTOMER_SESSION");
    }

    @Test
    void offersNoStaffAccountApi() throws Exception {
        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cat@example.com\",\"password\":\"correct horse battery\",\"role\":\"CATALOG_MANAGER\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ACCOUNT_CREATOR"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/users/00000000-0000-0000-0000-000000000000")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void neverBootstrapsAnAdmin() {
        assertThat(context.containsBean("bootstrapAdmin")).isFalse();
    }
}
