package com.oldnew.identity.adapter.out.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

/** Storage shape of a user account. Never leaves this package. */
@Document(collection = "users")
public record UserDocument(
        @Id String id,
        String email,
        String passwordHash,
        Set<String> roles,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        @Version Long version) {
}
