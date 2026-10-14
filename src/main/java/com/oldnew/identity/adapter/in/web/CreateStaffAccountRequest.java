package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A staff account an account creator opens for someone. The id is never taken from the request. */
public record CreateStaffAccountRequest(
        @Schema(example = "carol@oldnew.example") @NotBlank @Size(max = 254) String email,
        @Schema(example = "correct horse battery staple", minLength = 12, maxLength = 128)
        @NotBlank @Size(min = 12, max = 128) String password,
        @Schema(example = "CATALOG_MANAGER") @NotNull Role role) {

    @Override
    public String toString() {
        return "CreateStaffAccountRequest[email=<redacted>, password=<redacted>, role=" + role + "]";
    }
}
