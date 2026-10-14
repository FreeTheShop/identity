package com.oldnew.identity.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.oldnew.identity.support.StaffIntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production-like start: the prod profile is on, so ephemeral keys and {noop} secrets are refused.
 * Signing keys come from a mounted directory (like /run/secrets/jwt), the client secret is an Argon2id hash.
 * Two keys are mounted, as during a rotation: both are published, only the active one signs.
 */
@StaffIntegrationTest
@ActiveProfiles({"staff", "test", "prod"})
class MountedSecretKeysIT {

    private static final String CLIENT_SECRET = "prod-like-secret-" + System.nanoTime();
    private static final ECKey OLD_KEY = key("2026-10-a");
    private static final ECKey ACTIVE_KEY = key("2026-10-b");
    private static final Path KEYS_DIR = mount(OLD_KEY, ACTIVE_KEY);

    @DynamicPropertySource
    static void prodLikeSecrets(DynamicPropertyRegistry registry) {
        registry.add("identity.jwt.keys-dir", KEYS_DIR::toString);
        registry.add("identity.jwt.active-key-id", ACTIVE_KEY::getKeyID);
        registry.add("identity.jwt.allow-ephemeral-keys", () -> "false");
        registry.add("identity.security.allow-plaintext-client-secrets", () -> "false");
        registry.add("spring.security.oauth2.authorizationserver.client.price-service.registration.client-secret",
                () -> "{argon2id}" + new Argon2PasswordEncoder(16, 32, 1, 19_456, 2).encode(CLIENT_SECRET));
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcFactory.withSecurity(context);
    }

    @Test
    void tokensAreSignedWithTheMountedActiveKey() throws Exception {
        SignedJWT token = SignedJWT.parse(accessToken(CLIENT_SECRET));

        assertThat(token.getHeader().getKeyID()).isEqualTo(ACTIVE_KEY.getKeyID());
        assertThat(token.verify(new ECDSAVerifier(ACTIVE_KEY.toECPublicKey()))).isTrue();
        assertThat(token.verify(new ECDSAVerifier(OLD_KEY.toECPublicKey()))).isFalse();
    }

    @Test
    void jwksPublishesEveryMountedKeyButNoPrivateParts() throws Exception {
        String body = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JWKSet published = JWKSet.parse(body);

        assertThat(published.getKeys()).extracting(JWK::getKeyID)
                .containsExactlyInAnyOrder(OLD_KEY.getKeyID(), ACTIVE_KEY.getKeyID());
        assertThat(published.getKeys()).noneMatch(JWK::isPrivate);
        assertThat(body).doesNotContain("\"d\"");
    }

    @Test
    void hashedClientSecretIsCheckedAndAWrongOneIsRefused() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("price-service", "wrong-secret"))
                        .param("grant_type", "client_credentials").param("scope", "product:internal"))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken(String secret) throws Exception {
        String body = mvc.perform(post("/oauth2/token").with(httpBasic("price-service", secret))
                        .param("grant_type", "client_credentials").param("scope", "product:internal"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return (String) JSONObjectUtils.parse(body).get("access_token");
    }

    private static ECKey key(String kid) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path mount(ECKey... keys) {
        try {
            Path dir = Files.createTempDirectory("identity-jwt-");
            for (ECKey key : keys) {
                Files.writeString(dir.resolve(key.getKeyID() + ".json"), key.toJSONString());
            }
            Files.writeString(dir.resolve("README.txt"), "not a key; must be ignored");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
