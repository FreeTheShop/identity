package com.oldnew.identity.adapter.in.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;

/** Signed-in user. Carries the stable user id so tokens use it as "sub" instead of the e-mail. */
public class AccountPrincipal extends User {

    private final String userId;

    public AccountPrincipal(String userId, String email, String passwordHash, boolean locked,
                            Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, true, true, true, !locked, authorities);
        this.userId = userId;
    }

    public String userId() {
        return userId;
    }

    /** Role names without the ROLE_ prefix, for the "roles" token claim. */
    public List<String> roleNames() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
    }
}
