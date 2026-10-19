package com.oldnew.identity.api;

import com.oldnew.identity.support.MockMvcFactory;
import com.oldnew.identity.support.StaffIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Staff Identity (ADR-0009): the Admin UI and the service clients, no sign-up. Staff accounts come from an
 * ACCOUNT_CREATOR (interim: the creator sets the first password, until the Sprint 6 invite flow).
 */
@StaffIntegrationTest
class StaffInstanceIT {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    WebApplicationContext context;
    @Autowired
    RegisteredClientRepository clients;
    @Autowired
    MongoTemplate mongo;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mongo.remove(new Query(), "users");
        mvc = MockMvcFactory.withSecurity(context);
    }

    @Test
    void registersTheAdminUiAndTheServiceClientsButNotTheClientUi() {
        assertThat(clients.findByClientId("admin-ui")).isNotNull();
        assertThat(clients.findByClientId("price-service")).isNotNull();
        assertThat(clients.findByClientId("validation-service")).isNotNull();
        assertThat(clients.findByClientId("admin-aggregator-service")).isNotNull();
        assertThat(clients.findByClientId("client-aggregator-service")).isNotNull();
        assertThat(clients.findByClientId("client-ui")).isNull();
    }

    /** Browsers send cookies to every port of a host, so the two instances' sessions need different names. */
    @Test
    void sessionCookieHasItsOwnName() {
        assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("OLDNEW_STAFF_SESSION");
    }

    @Test
    void nobodyCanSignUp() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mallory@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(mongo.getCollection("users").countDocuments()).isZero();
    }

    @Test
    void accountCreatorCreatesACatalogManagerWhoCanSignIn() throws Exception {
        create("cat@example.com", "CATALOG_MANAGER", as("ACCOUNT_CREATOR"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/admin/users/")))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("CATALOG_MANAGER"))
                .andExpect(content().string(not(containsString("argon2"))))
                .andExpect(content().string(not(containsString(PASSWORD))));

        mvc.perform(formLogin("/login").user("cat@example.com").password(PASSWORD))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void onlyAnAccountCreatorMayCreateAccounts() throws Exception {
        create("cat@example.com", "CATALOG_MANAGER", as("ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        create("cat@example.com", "CATALOG_MANAGER", as("CATALOG_MANAGER"))
                .andExpect(status().isForbidden());

        assertThat(mongo.getCollection("users").countDocuments()).isZero();
    }

    /** ADMIN and ACCOUNT_CREATOR come only from the ADMIN-only role grant; staff are never customers. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNT_CREATOR", "CUSTOMER"})
    void accountCreatorCannotHandOutThisRole(String role) throws Exception {
        create("x@example.com", role, as("ACCOUNT_CREATOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        create("cat@example.com", "CATALOG_MANAGER", as("ACCOUNT_CREATOR")).andExpect(status().isCreated());

        create("Cat@Example.com", "CATALOG_MANAGER", as("ACCOUNT_CREATOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    private ResultActions create(String email, String role, RequestPostProcessor caller) throws Exception {
        return mvc.perform(post("/api/v1/admin/users").with(caller).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}"));
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("creator-1")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
