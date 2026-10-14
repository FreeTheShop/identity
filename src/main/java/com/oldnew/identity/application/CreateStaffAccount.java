package com.oldnew.identity.application;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.PasswordPolicy;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.PasswordHasher;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

/**
 * Use case: an account creator opens a staff account on the Staff Identity (ADR-0009). Audited.
 * <p>
 * Interim until the Sprint 6 invite flow: the creator sets the first password. ADMIN and ACCOUNT_CREATOR are
 * never handed out here, only through the ADMIN-only role grant, so an account creator cannot make an admin.
 */
@Service
public class CreateStaffAccount {

    /** Roles an account creator may give a new account. Seller roles join in Sprint 6. */
    static final Set<Role> ASSIGNABLE = EnumSet.of(Role.CATALOG_MANAGER);

    private static final Logger log = LoggerFactory.getLogger(CreateStaffAccount.class);

    private final UserAccountRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public CreateStaffAccount(UserAccountRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ACCOUNT_CREATOR')")
    public UserView handle(CreateStaffAccountCommand command, String actor) {
        if (!ASSIGNABLE.contains(command.role())) {
            throw new DomainException(ErrorCode.ROLE_NOT_ASSIGNABLE, "Staff accounts can be created with role " + ASSIGNABLE);
        }
        EmailAddress email = EmailAddress.of(command.email());
        PasswordPolicy.check(command.password(), email);
        if (users.existsByEmail(email)) {
            // The unique index still guards the race between this check and the insert.
            throw new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this e-mail already exists");
        }
        UserAccount account = UserAccount.createStaff(UserId.newId(), email, hasher.hash(command.password()),
                EnumSet.of(command.role()), clock.instant());
        UserAccount saved = users.save(account);
        log.info("audit.staff.created actor={} target={} role={}", actor, saved.id(), command.role());
        return UserView.of(saved);
    }
}
