package com.oldnew.identity.config.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Token buckets per (rule, caller), kept in memory with a bounded cache.
 * Good for one instance. With two or more instances, swap the cache for bucket4j-redis (roadmap: Next).
 */
public final class RateLimiter {

    private static final long MAX_TRACKED_CALLERS = 100_000;

    private final List<RateLimitRule> rules;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_CALLERS)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    public RateLimiter(List<RateLimitRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Optional<RateLimitRule> ruleFor(String method, String path) {
        return rules.stream().filter(r -> r.matches(method, path)).findFirst();
    }

    public Decision tryConsume(RateLimitRule rule, String callerKey) {
        Bucket bucket = buckets.get(rule.name() + '|' + callerKey, key -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Decision(true, probe.getRemainingTokens(), Duration.ZERO);
        }
        return new Decision(false, 0, Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private static Bucket newBucket(RateLimitRule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()).build())
                .build();
    }

    public record Decision(boolean allowed, long remaining, Duration retryAfter) {

        /** Whole seconds, rounded up, never below 1. */
        public long retryAfterSeconds() {
            return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        }
    }
}
