package com.oldnew.identity.domain.port;

import com.oldnew.identity.domain.model.PasswordHash;

/** Port for one-way password hashing. The adapter decides the algorithm (Argon2id). */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);
}
