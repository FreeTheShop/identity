package com.oldnew.identity.domain.model;

import java.util.Objects;

/** An encoded password ("{argon2id}$argon2id$..."). Never the raw password, never logged. */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[redacted]";
    }
}
