package com.oldnew.identity.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The ES256 keys Identity signs with. Loaded from a mounted secret directory: one private JWK per file.
 * <p>
 * Rotation: drop the next key in as a second file (it is published in JWKS straight away), wait at least
 * one access-token lifetime plus the JWKS cache time, switch {@code identity.jwt.active-key-id} to it,
 * then remove the old file after another token lifetime. See docs/runbooks/key-rotation.md.
 */
public final class SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

    private final JWKSet jwkSet;
    private final String activeKeyId;

    private SigningKeys(JWKSet jwkSet, String activeKeyId) {
        this.jwkSet = jwkSet;
        this.activeKeyId = activeKeyId;
    }

    public static SigningKeys load(Path keysDir, String activeKeyId, boolean allowEphemeral) {
        List<JWK> keys = readKeys(keysDir);
        if (keys.isEmpty()) {
            if (!allowEphemeral) {
                throw new IllegalStateException("No signing keys found in " + keysDir
                        + ". Mount the JWK secret, or set identity.jwt.allow-ephemeral-keys=true in dev only.");
            }
            ECKey ephemeral = generate("ephemeral-" + UUID.randomUUID());
            log.warn("jwt.keys.ephemeral kid={} tokens will not survive a restart (dev/test only)", ephemeral.getKeyID());
            return new SigningKeys(new JWKSet(ephemeral), ephemeral.getKeyID());
        }
        JWKSet set = new JWKSet(keys);
        JWK active = set.getKeyByKeyId(activeKeyId);
        if (active == null || !active.isPrivate()) {
            throw new IllegalStateException("identity.jwt.active-key-id '" + activeKeyId
                    + "' must name a private key present in " + keysDir);
        }
        log.info("jwt.keys.loaded count={} active={}", keys.size(), activeKeyId);
        return new SigningKeys(set, activeKeyId);
    }

    public JWKSet jwkSet() {
        return jwkSet;
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    static ECKey generate(String kid) {
        try {
            return new ECKeyGenerator(Curve.P_256)
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate an EC signing key", e);
        }
    }

    private static List<JWK> readKeys(Path dir) {
        List<JWK> keys = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return keys;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                JWK jwk = JWK.parse(Files.readString(file));
                if (!(jwk instanceof ECKey ec) || !Curve.P_256.equals(ec.getCurve())) {
                    throw new IllegalStateException("Key file " + file.getFileName() + " is not an EC P-256 JWK");
                }
                if (jwk.getKeyID() == null) {
                    throw new IllegalStateException("Key file " + file.getFileName() + " has no kid");
                }
                keys.add(jwk);
            }
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Could not read signing keys from " + dir, e);
        }
        return keys;
    }
}
