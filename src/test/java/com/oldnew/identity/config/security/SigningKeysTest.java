package com.oldnew.identity.config.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Startup must fail closed: no key, wrong key, or a public-only active key stops the service. */
class SigningKeysTest {

    @TempDir
    Path dir;

    @Test
    void loadsMountedKeysAndSignsWithTheNamedOne() throws Exception {
        write(SigningKeys.generate("a"));
        write(SigningKeys.generate("b"));
        Files.writeString(dir.resolve("notes.txt"), "ignored");

        SigningKeys keys = SigningKeys.load(dir, "b", false);

        assertThat(keys.activeKeyId()).isEqualTo("b");
        assertThat(keys.jwkSet().getKeys()).hasSize(2);
    }

    @Test
    void emptyDirectoryFailsWhenEphemeralKeysAreOff() {
        assertThatThrownBy(() -> SigningKeys.load(dir, "a", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No signing keys");
    }

    @Test
    void missingDirectoryFailsWhenEphemeralKeysAreOff() {
        assertThatThrownBy(() -> SigningKeys.load(dir.resolve("absent"), "a", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ephemeralKeyOnlyWhenExplicitlyAllowed() {
        assertThat(SigningKeys.load(dir, null, true).activeKeyId()).startsWith("ephemeral-");
    }

    @Test
    void activeKidMustBeMounted() throws Exception {
        write(SigningKeys.generate("a"));

        assertThatThrownBy(() -> SigningKeys.load(dir, "z", false)).hasMessageContaining("active-key-id");
    }

    @Test
    void activeKeyMustBePrivate() throws Exception {
        write(SigningKeys.generate("a").toPublicJWK());

        assertThatThrownBy(() -> SigningKeys.load(dir, "a", false)).hasMessageContaining("private key");
    }

    @Test
    void onlyP256KeysAreAccepted() throws Exception {
        write(new ECKeyGenerator(Curve.P_384).keyID("big").generate());

        assertThatThrownBy(() -> SigningKeys.load(dir, "big", false)).hasMessageContaining("P-256");
    }

    private void write(ECKey key) throws Exception {
        Files.writeString(dir.resolve(key.getKeyID() + ".json"), key.toJSONString());
    }
}
