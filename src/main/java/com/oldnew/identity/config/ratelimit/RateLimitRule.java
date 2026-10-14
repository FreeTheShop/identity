package com.oldnew.identity.config.ratelimit;

import java.time.Duration;
import java.util.Locale;

/**
 * One inbound limit: at most {@code capacity} requests per {@code period} per caller, on paths starting
 * with {@code pathPrefix} (and, if set, only for {@code method}).
 */
public record RateLimitRule(String name, String method, String pathPrefix, long capacity, Duration period) {

    public RateLimitRule {
        if (name == null || name.isBlank() || pathPrefix == null || !pathPrefix.startsWith("/")) {
            throw new IllegalArgumentException("Rate limit rule needs a name and a path prefix starting with /");
        }
        if (capacity < 1 || period == null || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("Rate limit rule " + name + " needs capacity >= 1 and a positive period");
        }
        method = method == null || method.isBlank() ? null : method.toUpperCase(Locale.ROOT);
    }

    public boolean matches(String requestMethod, String path) {
        return path.startsWith(pathPrefix) && (method == null || method.equalsIgnoreCase(requestMethod));
    }
}
