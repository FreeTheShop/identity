package com.oldnew.identity.application;

/** Input for {@link RegisterUser}. toString never prints the password. */
public record RegisterUserCommand(String email, String password) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=<redacted>, password=<redacted>]";
    }
}
