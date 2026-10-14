package com.oldnew.identity.config.security;

import com.oldnew.identity.config.IdentityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Argon2id at the OWASP minimum (19 MiB memory, 2 iterations, parallelism 1) for new hashes.
 * BCrypt is kept only to verify old hashes during a migration. Hashes are stored with their
 * algorithm prefix, so the parameters can be raised later without breaking existing accounts.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncoderConfig {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder(IdentityProperties properties, Environment environment) {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2id", new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS));
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        if (properties.security().allowPlaintextClientSecrets()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("identity.security.allow-plaintext-client-secrets must be false in prod");
            }
            encoders.put("noop", plaintextForDevOnly());
        }
        return new DelegatingPasswordEncoder("argon2id", encoders);
    }

    @SuppressWarnings("deprecation")
    private static PasswordEncoder plaintextForDevOnly() {
        return NoOpPasswordEncoder.getInstance();
    }
}
