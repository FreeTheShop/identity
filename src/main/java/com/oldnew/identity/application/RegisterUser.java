package com.oldnew.identity.application;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.PasswordPolicy;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.PasswordHasher;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Use case: a person creates an account. New accounts are always CUSTOMER only. */
@Service
public class RegisterUser {

    private static final Logger log = LoggerFactory.getLogger(RegisterUser.class);

    private final UserAccountRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUser(UserAccountRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    public UserView handle(RegisterUserCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PasswordPolicy.check(command.password(), email);
        if (users.existsByEmail(email)) {
            // The unique index still guards the race between this check and the insert.
            throw new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this e-mail already exists");
        }
        UserAccount account = UserAccount.register(UserId.newId(), email, hasher.hash(command.password()), clock.instant());
        UserAccount saved = users.save(account);
        log.info("user.registered userId={}", saved.id());
        return UserView.of(saved);
    }
}
