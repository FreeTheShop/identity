package com.oldnew.identity.adapter.in.security;

import com.oldnew.identity.application.LoginAttempts;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Feeds password sign-in outcomes into {@link LoginAttempts}.
 * Only username/password events count; client and bearer-token authentications are ignored.
 */
@Component
class LoginOutcomeListener {

    private final LoginAttempts loginAttempts;

    LoginOutcomeListener(LoginAttempts loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @EventListener
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token) {
            loginAttempts.recordFailure(token.getName());
        }
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AccountPrincipal principal) {
            loginAttempts.recordSuccess(principal.getUsername());
        }
    }
}
