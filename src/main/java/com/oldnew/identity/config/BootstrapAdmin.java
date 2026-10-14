package com.oldnew.identity.config;

import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.PasswordPolicy;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.PasswordHasher;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

/**
 * Creates the first admin if identity.bootstrap-admin.* is set: a staff account with ADMIN and ACCOUNT_CREATOR,
 * so it can create the other staff accounts. Staff Identity only (ADR-0009).
 * Idempotent: safe on every start. Remove the secret once a real admin exists.
 */
@Component
@Profile("staff")
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private static final Set<Role> ROLES = EnumSet.of(Role.ADMIN, Role.ACCOUNT_CREATOR);

    private final IdentityProperties properties;
    private final UserAccountRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    BootstrapAdmin(IdentityProperties properties, UserAccountRepository users, PasswordHasher hasher, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        IdentityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.configured()) {
            return;
        }
        EmailAddress email = EmailAddress.of(admin.email());
        if (!users.existsByEmail(email)) {
            PasswordPolicy.check(admin.password(), email);
            UserId id = users.save(UserAccount.createStaff(UserId.newId(), email, hasher.hash(admin.password()),
                    ROLES, clock.instant())).id();
            log.warn("audit.staff.created actor=bootstrap target={} roles={}", id, ROLES);
            return;
        }
        UserAccount account = users.findByEmail(email).orElseThrow();
        Set<Role> missing = EnumSet.copyOf(ROLES);
        missing.removeAll(account.roles());
        if (!missing.isEmpty()) {
            missing.forEach(account::grant);
            users.save(account);
            log.warn("audit.role.granted actor=bootstrap target={} roles={}", account.id(), missing);
        }
    }
}
