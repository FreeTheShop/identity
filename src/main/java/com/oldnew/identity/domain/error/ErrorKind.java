package com.oldnew.identity.domain.error;

/**
 * What kind of failure a domain error is. The web adapter maps each kind to an HTTP status,
 * so the domain never knows about HTTP.
 */
public enum ErrorKind {
    /** The caller sent something malformed. */
    INVALID_INPUT,
    /** The thing the caller asked for does not exist (or is not visible to them). */
    NOT_FOUND,
    /** The request clashes with current state (duplicate, concurrent change). */
    CONFLICT,
    /** The request is well-formed but breaks a business rule. */
    RULE_VIOLATION
}
