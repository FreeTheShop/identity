package com.oldnew.identity.domain.error;

/**
 * Stable error codes. Clients branch on these, so never rename one; add a new code and deprecate the old.
 */
public enum ErrorCode {
    VALIDATION_FAILED(ErrorKind.INVALID_INPUT),
    INVALID_EMAIL(ErrorKind.INVALID_INPUT),
    WEAK_PASSWORD(ErrorKind.INVALID_INPUT),
    INVALID_USER_ID(ErrorKind.INVALID_INPUT),
    /** An account creator asked for a role they may not hand out (ADR-0009). */
    ROLE_NOT_ASSIGNABLE(ErrorKind.INVALID_INPUT),
    EMAIL_ALREADY_REGISTERED(ErrorKind.CONFLICT),
    CONCURRENT_UPDATE(ErrorKind.CONFLICT),
    USER_NOT_FOUND(ErrorKind.NOT_FOUND);

    private final ErrorKind kind;

    ErrorCode(ErrorKind kind) {
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
