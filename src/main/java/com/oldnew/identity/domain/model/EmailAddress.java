package com.oldnew.identity.domain.model;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/** A normalised (trimmed, lower-case) e-mail address; the user's login name. */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_EMAIL, "E-mail is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new DomainException(ErrorCode.INVALID_EMAIL, "E-mail is not valid");
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /** Masked form for logs, e.g. "a***@example.com". */
    public String masked() {
        int at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }

    @Override
    public String toString() {
        return masked();
    }
}
