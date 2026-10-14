package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.application.GetUser;
import com.oldnew.identity.application.GrantRole;
import com.oldnew.identity.application.UserView;
import com.oldnew.identity.domain.model.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only. The path rule requires ROLE_ADMIN and the use case checks it again. */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users", description = "Requires the ADMIN role")
class AdminUserController {

    private final GetUser getUser;
    private final GrantRole grantRole;

    AdminUserController(GetUser getUser, GrantRole grantRole) {
        this.getUser = getUser;
        this.grantRole = grantRole;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read any account")
    UserView get(@PathVariable String id) {
        return getUser.handle(new UserId(id));
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "Grant a role", description = "Takes effect at the user's next token (within 10 minutes).")
    UserView grant(@PathVariable String id, @Valid @RequestBody GrantRoleRequest request, @AuthenticationPrincipal Jwt jwt) {
        return grantRole.handle(new UserId(id), request.role(), jwt.getSubject());
    }
}
