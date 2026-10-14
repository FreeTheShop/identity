package com.oldnew.identity.application;

import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/** What the outside world may see of an account. No hash, no lockout counters. */
public record UserView(String id, String email, Set<String> roles, Instant createdAt) {

    public static UserView of(UserAccount account) {
        Set<String> roleNames = new TreeSet<>();
        for (Role role : account.roles()) {
            roleNames.add(role.name());
        }
        return new UserView(account.id().value(), account.email().value(), roleNames, account.createdAt());
    }
}
