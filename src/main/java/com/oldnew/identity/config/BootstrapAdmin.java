package com.oldnew.identity.config;

import com.oldnew.identity.application.RegisterUser;
import com.oldnew.identity.application.RegisterUserCommand;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN if identity.bootstrap-admin.* is set and the account does not exist yet.
 * Idempotent: safe on every start. Remove the secret once a real admin exists.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final IdentityProperties properties;
    private final RegisterUser registerUser;
    private final UserAccountRepository users;

    BootstrapAdmin(IdentityProperties properties, RegisterUser registerUser, UserAccountRepository users) {
        this.properties = properties;
        this.registerUser = registerUser;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        IdentityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.configured()) {
            return;
        }
        EmailAddress email = EmailAddress.of(admin.email());
        if (!users.existsByEmail(email)) {
            registerUser.handle(new RegisterUserCommand(admin.email(), admin.password()));
        }
        UserAccount account = users.findByEmail(email).orElseThrow();
        if (!account.hasRole(Role.ADMIN)) {
            account.grant(Role.ADMIN);
            UserId id = users.save(account).id();
            log.warn("audit.role.granted actor=bootstrap target={} role=ADMIN", id);
        }
    }
}
