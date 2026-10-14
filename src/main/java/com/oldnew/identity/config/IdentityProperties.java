package com.oldnew.identity.config;

import com.oldnew.identity.config.ratelimit.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Every Identity-specific setting, bound from {@code identity.*}. Defaults live in application.yml.
 */
@ConfigurationProperties("identity")
public record IdentityProperties(
        Jwt jwt,
        Lockout lockout,
        Token token,
        List<RateLimitRule> rateLimits,
        Security security,
        BootstrapAdmin bootstrapAdmin) {

    /**
     * @param keysDir            directory with one private JWK (*.json) per signing key, mounted from a secret
     * @param activeKeyId        the kid that signs new tokens; the others are only published for verification
     * @param allowEphemeralKeys dev/test only: generate a throw-away key when the directory is empty
     */
    public record Jwt(Path keysDir, String activeKeyId, boolean allowEphemeralKeys) {
    }

    public record Lockout(int maxFailedAttempts, Duration lockDuration) {
    }

    /**
     * @param audienceByScopePrefix scope prefix to audience, e.g. "product" -> "product-service":
     *                              a token carrying any "product:*" scope gets aud "product-service"
     */
    public record Token(Map<String, String> audienceByScopePrefix) {
    }

    /**
     * @param allowPlaintextClientSecrets dev/test only: accept "{noop}" client secrets. Refused in prod.
     */
    public record Security(boolean allowPlaintextClientSecrets) {
    }

    /** Optional first admin, created at startup if absent. Supply both values from secrets, never from Git. */
    public record BootstrapAdmin(String email, String password) {
        public boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
