package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.application.CreateStaffAccount;
import com.oldnew.identity.application.CreateStaffAccountCommand;
import com.oldnew.identity.application.GetUser;
import com.oldnew.identity.application.GrantRole;
import com.oldnew.identity.application.UserView;
import com.oldnew.identity.domain.model.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Staff Identity only (ADR-0009). Creating an account needs ROLE_ACCOUNT_CREATOR; everything else ROLE_ADMIN.
 * The path rules require the role and the use cases check it again.
 */
@RestController
@Profile("staff")
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users", description = "Requires the ADMIN role, or ACCOUNT_CREATOR to create an account")
class AdminUserController {

    private final GetUser getUser;
    private final GrantRole grantRole;
    private final CreateStaffAccount createStaffAccount;

    AdminUserController(GetUser getUser, GrantRole grantRole, CreateStaffAccount createStaffAccount) {
        this.getUser = getUser;
        this.grantRole = grantRole;
        this.createStaffAccount = createStaffAccount;
    }

    @PostMapping
    @Operation(summary = "Create a staff account",
            description = "Needs ACCOUNT_CREATOR. Role CATALOG_MANAGER only. Interim until the Sprint 6 invite flow: "
                    + "the creator sets the first password.")
    ResponseEntity<UserView> create(@Valid @RequestBody CreateStaffAccountRequest request, @AuthenticationPrincipal Jwt jwt) {
        UserView user = createStaffAccount.handle(
                new CreateStaffAccountCommand(request.email(), request.password(), request.role()), jwt.getSubject());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/admin/users/{id}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(user);
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
