package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.domain.model.Role;
import jakarta.validation.constraints.NotNull;

public record GrantRoleRequest(@NotNull Role role) {
}
