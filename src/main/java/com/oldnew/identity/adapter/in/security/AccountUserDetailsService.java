package com.oldnew.identity.adapter.in.security;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Bridges Spring Security's form login to the account aggregate.
 * Spring hides "user not found" behind the same "bad credentials" response and runs a dummy hash,
 * so response and timing do not reveal which e-mails exist.
 */
@Component
class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;
    private final Clock clock;

    AccountUserDetailsService(UserAccountRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        EmailAddress email;
        try {
            email = EmailAddress.of(username);
        } catch (DomainException e) {
            throw new UsernameNotFoundException("Bad credentials");
        }
        UserAccount account = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
        return new AccountPrincipal(
                account.id().value(),
                account.email().value(),
                account.passwordHash().value(),
                account.isLockedAt(clock.instant()),
                account.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList());
    }
}
