package com.oldnew.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

/** How many failed sign-ins lock an account, and for how long. */
public record LockoutPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LockoutPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts must be at least 1");
        }
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
    }

    public static LockoutPolicy standard() {
        return new LockoutPolicy(5, Duration.ofMinutes(15));
    }
}
