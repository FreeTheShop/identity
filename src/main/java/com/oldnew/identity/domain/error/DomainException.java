package com.oldnew.identity.domain.error;

import java.util.Objects;

/**
 * A business failure with a stable code. The message is safe to show to the caller:
 * never put secrets, hashes or other users' data in it.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }
}
