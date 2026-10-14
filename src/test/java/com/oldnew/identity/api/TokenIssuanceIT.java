package com.oldnew.identity.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.oldnew.identity.support.IntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The contract Product relies on: ES256, a kid in JWKS, aud "product-service", 10-minute lifetime. */
@IntegrationTest
class TokenIssuanceIT {

    @Autowired
    WebApplicationContext context;

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
