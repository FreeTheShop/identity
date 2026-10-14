package com.oldnew.identity.application;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.LockoutPolicy;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Records sign-in outcomes against the account so brute force locks it.
 * Unknown e-mails are ignored silently: the caller sees the same response either way.
 */
@Service
public class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);
    private static final int MAX_CONCURRENT_RETRIES = 3;

    private final UserAccountRepository users;
    private final LockoutPolicy policy;
    private final Clock clock;

    public LoginAttempts(UserAccountRepository users, LockoutPolicy policy, Clock clock) {
        this.users = users;
        this.policy = policy;
        this.clock = clock;
    }

    public void recordFailure(String loginName) {
        Instant now = clock.instant();
        update(loginName, account -> account.recordFailedLogin(now, policy)).ifPresent(saved -> {
            if (saved.isLockedAt(now)) {
                log.warn("login.locked userId={} until={}", saved.id(), saved.lockedUntil());
            } else {
                log.info("login.failed userId={} attempts={}", saved.id(), saved.failedLoginAttempts());
            }
        });
    }

    public void recordSuccess(String loginName) {
        update(loginName, UserAccount::recordSuccessfulLogin);
    }

    /**
     * Load, change, save; if another sign-in saved the same account in between, reload and apply the change
     * again (at most a few times). Returns the saved account, or empty for an unknown login name.
     */
    private Optional<UserAccount> update(String loginName, Consumer<UserAccount> change) {
        Optional<EmailAddress> email = parse(loginName);
        if (email.isEmpty()) {
            return Optional.empty();
        }
        for (int attempt = 1; ; attempt++) {
            Optional<UserAccount> account = users.findByEmail(email.get());
            if (account.isEmpty()) {
                return Optional.empty();
            }
            change.accept(account.get());
            try {
                return Optional.of(users.save(account.get()));
            } catch (DomainException e) {
                if (e.code() != ErrorCode.CONCURRENT_UPDATE || attempt >= MAX_CONCURRENT_RETRIES) {
                    throw e;
                }
            }
        }
    }

    private static Optional<EmailAddress> parse(String loginName) {
        try {
            return Optional.of(EmailAddress.of(loginName));
        } catch (DomainException e) {
            return Optional.empty();
        }
    }
}
