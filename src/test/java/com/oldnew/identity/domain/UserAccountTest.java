package com.oldnew.identity.domain;

import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.LockoutPolicy;
import com.oldnew.identity.domain.model.PasswordHash;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final LockoutPolicy POLICY = new LockoutPolicy(5, Duration.ofMinutes(15));

    private static UserAccount newAccount() {
        return UserAccount.register(UserId.newId(), EmailAddress.of("Alice@Example.com"),
                new PasswordHash("{argon2id}hash"), NOW);
    }

    @Test
    void newAccountIsCustomerOnlyAndUnlocked() {
        UserAccount account = newAccount();

        assertThat(account.roles()).containsExactly(Role.CUSTOMER);
        assertThat(account.isLockedAt(NOW)).isFalse();
        assertThat(account.email().value()).isEqualTo("alice@example.com");
        assertThat(account.version()).isNull();
    }

    @Test
    void fourFailuresDoNotLock() {
        UserAccount account = newAccount();

        for (int i = 0; i < 4; i++) {
            account.recordFailedLogin(NOW, POLICY);
        }

        assertThat(account.isLockedAt(NOW)).isFalse();
        assertThat(account.failedLoginAttempts()).isEqualTo(4);
    }

    @Test
    void fifthFailureLocksForFifteenMinutes() {
        UserAccount account = newAccount();

        for (int i = 0; i < 5; i++) {
            account.recordFailedLogin(NOW, POLICY);
        }

        assertThat(account.isLockedAt(NOW)).isTrue();
        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void failuresWhileLockedDoNotExtendTheLock() {
        UserAccount account = newAccount();
        for (int i = 0; i < 5; i++) {
            account.recordFailedLogin(NOW, POLICY);
        }
        Instant lockedUntil = account.lockedUntil();

        account.recordFailedLogin(NOW.plus(Duration.ofMinutes(10)), POLICY);

        assertThat(account.lockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void afterLockExpiresTheCountStartsAgain() {
        UserAccount account = newAccount();
        for (int i = 0; i < 5; i++) {
            account.recordFailedLogin(NOW, POLICY);
        }

        account.recordFailedLogin(NOW.plus(Duration.ofMinutes(16)), POLICY);

        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(account.failedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void successfulLoginClearsFailures() {
        UserAccount account = newAccount();
        account.recordFailedLogin(NOW, POLICY);
        account.recordFailedLogin(NOW, POLICY);

        account.recordSuccessfulLogin();

        assertThat(account.failedLoginAttempts()).isZero();
        assertThat(account.lockedUntil()).isNull();
    }

    @Test
    void customerRoleCannotBeLostWhenRebuiltFromStorage() {
        UserAccount account = UserAccount.reconstitute(UserId.newId(), EmailAddress.of("bob@example.com"),
                new PasswordHash("{argon2id}hash"), java.util.Set.of(Role.ADMIN), 0, null, NOW, 3L);

        assertThat(account.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CUSTOMER);
    }
}
