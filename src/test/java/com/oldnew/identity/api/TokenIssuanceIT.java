package com.oldnew.identity.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.oldnew.identity.support.StaffIntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The contract the services rely on: ES256, a kid in JWKS, aud "<service>-service" per scope, 10-minute lifetime.
 * On the Staff Identity, which holds the service clients and the Admin UI (ADR-0009).
 */
@StaffIntegrationTest
class TokenIssuanceIT {

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
    void clientCredentialsTokenIsSignedForProduct() throws Exception {
        String body = mvc.perform(post("/oauth2/token")
                        .with(httpBasic("price-service", "test-price-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "product:internal"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> response = JSONObjectUtils.parse(body);
        SignedJWT token = SignedJWT.parse((String) response.get("access_token"));

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(token.getJWTClaimsSet().getAudience()).contains("product-service");
        assertThat(token.getJWTClaimsSet().getStringListClaim("scope")).containsExactly("product:internal");
        long lifetimeSeconds = (token.getJWTClaimsSet().getExpirationTime().getTime()
                - token.getJWTClaimsSet().getIssueTime().getTime()) / 1000;
        assertThat(lifetimeSeconds).isEqualTo(600);

        String jwks = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JWKSet keys = JWKSet.parse(jwks);
        ECKey key = (ECKey) keys.getKeyByKeyId(token.getHeader().getKeyID());
        assertThat(key).isNotNull();
        assertThat(key.isPrivate()).as("JWKS must never publish the private part").isFalse();
        assertThat(token.verify(new ECDSAVerifier(key))).isTrue();
    }

    /** Sprint 2 starters: each service's read scope adds that service, and only that service, to "aud". */
    @ParameterizedTest
    @CsvSource({
            "price:read, price-service",
            "validation:read, validation-service",
            "admin-core:read, admin-core-service",
            "admin-aggregator:read, admin-aggregator-service",
            "client-core:read, client-core-service",
            "client-aggregator:read, client-aggregator-service"})
    void eachServiceScopeYieldsItsOwnAudience(String scope, String audience) throws Exception {
        SignedJWT token = clientCredentials("scope-probe", "test-probe-secret", scope);

        assertThat(token.getJWTClaimsSet().getStringListClaim("scope")).containsExactly(scope);
        assertThat(token.getJWTClaimsSet().getAudience()).containsExactlyInAnyOrder("scope-probe", audience);
    }

    @Test
    void priceAndValidationGetTokensForTheirOwnStatus() throws Exception {
        assertThat(clientCredentials("price-service", "test-price-secret", "price:read").getJWTClaimsSet().getAudience())
                .contains("price-service");
        assertThat(clientCredentials("validation-service", "test-validation-secret", "validation:read").getJWTClaimsSet().getAudience())
                .contains("validation-service");
        assertThat(clientCredentials("validation-service", "test-validation-secret", "product:internal").getJWTClaimsSet().getAudience())
                .contains("product-service");
    }

    /** Price's Core Transactional: the price at checkout time, for Validation. */
    @Test
    void validationGetsAPriceTokenForCheckout() throws Exception {
        SignedJWT token = clientCredentials("validation-service", "test-validation-secret", "price:internal");

        assertThat(token.getJWTClaimsSet().getStringListClaim("scope")).containsExactly("price:internal");
        assertThat(token.getJWTClaimsSet().getAudience()).contains("price-service").doesNotContain("product-service");
    }

    /** The Admin UI asks for its aggregator's scope plus the scopes of the services the aggregator relays the token to. */
    @Test
    void adminUiMayAskForItsAggregatorAndWhatItCalls() {
        assertThat(clients.findByClientId("admin-ui").getScopes())
                .contains("admin-aggregator:read", "admin-core:read", "product:read");
    }

    /** Golden SKU flow: catalog managers save SKUs in Admin Core and may start the SKU sync job now. */
    @Test
    void adminUiMayAskToSaveSkusAndRunTheSync() {
        assertThat(clients.findByClientId("admin-ui").getScopes()).contains("admin-core:write", "admin-aggregator:write");
    }

    /** Golden SKU flow: the SKU sync job reads Admin Core's changes with its own token, and can do nothing else. */
    @Test
    void adminAggregatorReadsAdminCoreChanges() throws Exception {
        SignedJWT token = clientCredentials("admin-aggregator-service", "test-admin-aggregator-secret", "admin-core:internal");

        assertThat(token.getJWTClaimsSet().getStringListClaim("scope")).containsExactly("admin-core:internal");
        assertThat(token.getJWTClaimsSet().getAudience()).containsExactlyInAnyOrder("admin-aggregator-service", "admin-core-service");
        assertThat(clients.findByClientId("admin-aggregator-service").getScopes()).containsExactly("admin-core:internal");
    }

    /** Golden SKU flow: the Client aggregator reads a SKU from Product and writes its shop view to Client Core. */
    @Test
    void clientAggregatorReadsProductAndWritesClientCore() throws Exception {
        SignedJWT token = clientCredentials("client-aggregator-service", "test-client-aggregator-secret",
                "product:internal client-core:internal");

        assertThat(token.getJWTClaimsSet().getStringListClaim("scope"))
                .containsExactlyInAnyOrder("product:internal", "client-core:internal");
        assertThat(token.getJWTClaimsSet().getAudience())
                .containsExactlyInAnyOrder("client-aggregator-service", "product-service", "client-core-service");
        assertThat(clients.findByClientId("client-aggregator-service").getScopes())
                .containsExactlyInAnyOrder("product:internal", "client-core:internal");
    }

    @Test
    void aggregatorsCannotWriteProducts() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("client-aggregator-service", "test-client-aggregator-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "product:write"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    private SignedJWT clientCredentials(String clientId, String secret, String scope) throws Exception {
        String body = mvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, secret))
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return SignedJWT.parse((String) JSONObjectUtils.parse(body).get("access_token"));
    }

    /** The UIs exchange their code from JavaScript: only allowlisted origins (test profile: localhost:3000) may. */
    @Test
    void onlyAllowlistedBrowserAppsMayCallTheTokenEndpoint() throws Exception {
        mvc.perform(options("/oauth2/token")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mvc.perform(options("/oauth2/token")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void wrongClientSecretIsRejected() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("price-service", "wrong"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "product:internal"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotAskForAScopeItWasNotGiven() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("price-service", "test-price-secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", "product:write"))
                .andExpect(status().isBadRequest());
    }
}
