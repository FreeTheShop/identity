package com.oldnew.identity.config.security;

import com.oldnew.identity.config.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordEncoderConfigTest {

    private final PasswordEncoderConfig config = new PasswordEncoderConfig();

    @Test
    void plaintextClientSecretsAreRefusedUnderProd() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.passwordEncoder(properties(true), prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be false in prod");
    }

    @Test
    void newHashesAreArgon2idAndNoopIsUnknownWhenOff() {
        PasswordEncoder encoder = config.passwordEncoder(properties(false), new MockEnvironment());

        assertThat(encoder.encode("correct horse battery")).startsWith("{argon2id}");
        assertThatThrownBy(() -> encoder.matches("x", "{noop}x")).isInstanceOf(IllegalArgumentException.class);
    }

    private static IdentityProperties properties(boolean allowPlaintext) {
        return new IdentityProperties(null, null, null, List.of(), new IdentityProperties.Security(allowPlaintext), null, null);
    }
}
