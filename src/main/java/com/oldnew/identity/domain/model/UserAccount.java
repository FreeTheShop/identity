package com.oldnew.identity.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root for a person who can sign in.
 * <p>
 * Invariants:
 * <ul>
 *     <li>Every account has at least the CUSTOMER role.</li>
 *     <li>An account is locked once failed sign-ins reach the policy limit, until the lock expires.</li>
 *     <li>A successful sign-in clears the failure count.</li>
 * </ul>
 * State changes only through methods; there are no setters.
 */
public final class UserAccount {

    private final UserId id;
    private final EmailAddress email;
    private final PasswordHash passwordHash;
    private final Set<Role> roles;
    private final Instant createdAt;
    private final Long version;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private UserAccount(UserId id, EmailAddress email, PasswordHash passwordHash, Set<Role> roles,
                        int failedLoginAttempts, Instant lockedUntil, Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.roles.add(Role.CUSTOMER);
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts must not be negative");
        }
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.version = version;
    }

    /** A brand-new account. Every new account is a CUSTOMER. */
    public static UserAccount register(UserId id, EmailAddress email, PasswordHash passwordHash, Instant now) {
        return new UserAccount(id, email, passwordHash, EnumSet.of(Role.CUSTOMER), 0, null, now, null);
    }

    /** Rebuild from storage. Only persistence adapters call this. */
    public static UserAccount reconstitute(UserId id, EmailAddress email, PasswordHash passwordHash, Set<Role> roles,
                                           int failedLoginAttempts, Instant lockedUntil, Instant createdAt,
                                           Long version) {
        return new UserAccount(id, email, passwordHash, roles, failedLoginAttempts, lockedUntil, createdAt, version);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Count a failed sign-in. Failures while locked are ignored, so an attacker cannot extend a lock forever.
     * Once a lock has expired the count starts again from zero.
     */
    public void recordFailedLogin(Instant now, LockoutPolicy policy) {
        if (isLockedAt(now)) {
            return;
        }
        if (lockedUntil != null) {
            lockedUntil = null;
            failedLoginAttempts = 0;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void grant(Role role) {
        roles.add(Objects.requireNonNull(role, "role"));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public UserId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Optimistic-locking token; null until first saved. */
    public Long version() {
        return version;
    }
}
