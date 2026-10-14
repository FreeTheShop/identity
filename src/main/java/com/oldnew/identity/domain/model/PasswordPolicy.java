package com.oldnew.identity.domain.model;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;

/**
 * Rules a new password must meet. Length beats composition rules (NIST SP 800-63B):
 * 12 to 128 characters and not the e-mail address itself.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void check(String rawPassword, EmailAddress email) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new DomainException(ErrorCode.WEAK_PASSWORD,
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.WEAK_PASSWORD,
                    "Password must be at most " + MAX_LENGTH + " characters");
        }
        if (rawPassword.equalsIgnoreCase(email.value())) {
            throw new DomainException(ErrorCode.WEAK_PASSWORD, "Password must not be the e-mail address");
        }
    }
}
