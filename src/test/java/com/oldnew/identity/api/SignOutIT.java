package com.oldnew.identity.api;

import com.oldnew.identity.support.StaffIntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing out of the Admin UI ends the Identity session too (OIDC RP-initiated logout), so the next sign-in
 * shows the login form. Identity only redirects back to addresses registered for the client.
 * The Client UI's address is checked in CustomerInstanceIT.
 */
@StaffIntegrationTest
class SignOutIT {

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
    void discoveryAdvertisesTheLogoutEndpoint() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end_session_endpoint", endsWith("/connect/logout")));
    }

    @Test
    void adminUiReturnsToItsHomePageAfterSigningOut() {
        assertThat(clients.findByClientId("admin-ui").getPostLogoutRedirectUris())
                .containsExactly("http://localhost:3000/");
    }
}
