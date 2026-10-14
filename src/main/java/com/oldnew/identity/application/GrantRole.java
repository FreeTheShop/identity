package com.oldnew.identity.application;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Use case: an admin gives a user a role. Audited: who granted what to whom.
 * Takes effect at the user's next token (access tokens live 10 minutes).
 */
@Service
public class GrantRole {

    private static final Logger log = LoggerFactory.getLogger(GrantRole.class);

    private final UserAccountRepository users;

    public GrantRole(UserAccountRepository users) {
        this.users = users;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserView handle(UserId target, Role role, String actor) {
        UserAccount account = users.findById(target)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND, "User not found"));
        account.grant(role);
        UserAccount saved = users.save(account);
        log.info("audit.role.granted actor={} target={} role={}", actor, target, role);
        return UserView.of(saved);
    }
}
