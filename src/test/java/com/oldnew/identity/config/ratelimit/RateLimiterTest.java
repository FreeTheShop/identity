package com.oldnew.identity.config.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimitRule login = new RateLimitRule("login", "post", "/login", 2, Duration.ofMinutes(1));
    private final RateLimitRule api = new RateLimitRule("api", null, "/api/", 100, Duration.ofMinutes(1));
    private final RateLimiter limiter = new RateLimiter(List.of(login, api));

    @Test
    void firstMatchingRuleWinsAndMethodIsCaseInsensitive() {
        assertThat(limiter.ruleFor("POST", "/login")).contains(login);
        assertThat(limiter.ruleFor("GET", "/login")).isEmpty();
        assertThat(limiter.ruleFor("DELETE", "/api/v1/users/1")).contains(api);
    }

    @Test
    void capacityIsPerCallerAndRefusalSaysWhenToRetry() {
        assertThat(limiter.tryConsume(login, "ip:1").allowed()).isTrue();
        assertThat(limiter.tryConsume(login, "ip:1").allowed()).isTrue();

        RateLimiter.Decision refused = limiter.tryConsume(login, "ip:1");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 60L);

        assertThat(limiter.tryConsume(login, "ip:2").allowed()).isTrue();
    }

    @Test
    void retryAfterRoundsUpAndIsNeverZero() {
        assertThat(new RateLimiter.Decision(false, 0, Duration.ofMillis(1)).retryAfterSeconds()).isEqualTo(1);
        assertThat(new RateLimiter.Decision(false, 0, Duration.ofMillis(1001)).retryAfterSeconds()).isEqualTo(2);
        assertThat(new RateLimiter.Decision(false, 0, Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
    }
}
