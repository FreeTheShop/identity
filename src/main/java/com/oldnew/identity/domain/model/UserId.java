package com.oldnew.identity.domain.model;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;

import java.util.UUID;

/** Identity of a user account. Opaque, non-guessable, never reused. */
public record UserId(String value) {

    public UserId {
        if (value == null) {
            throw new DomainException(ErrorCode.INVALID_USER_ID, "User id is required");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.INVALID_USER_ID, "User id is not valid", e);
        }
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
