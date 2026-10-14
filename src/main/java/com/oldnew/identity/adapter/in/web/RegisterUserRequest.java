package com.oldnew.identity.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only these two fields can be set at sign-up; roles and ids are never taken from the request. */
public record RegisterUserRequest(
        @Schema(example = "alice@example.com") @NotBlank @Size(max = 254) String email,
        @Schema(example = "correct horse battery staple", minLength = 12, maxLength = 128)
        @NotBlank @Size(min = 12, max = 128) String password) {

    @Override
    public String toString() {
        return "RegisterUserRequest[email=<redacted>, password=<redacted>]";
    }
}
