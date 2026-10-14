package com.oldnew.identity.application;

import com.oldnew.identity.domain.model.Role;

/** Input for {@link CreateStaffAccount}. toString never prints the password. */
public record CreateStaffAccountCommand(String email, String password, Role role) {

    @Override
    public String toString() {
        return "CreateStaffAccountCommand[email=<redacted>, password=<redacted>, role=" + role + "]";
    }
}
