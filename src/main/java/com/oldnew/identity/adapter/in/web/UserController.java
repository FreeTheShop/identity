package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.application.GetUser;
import com.oldnew.identity.application.RegisterUser;
import com.oldnew.identity.application.RegisterUserCommand;
import com.oldnew.identity.application.UserView;
import com.oldnew.identity.domain.model.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Self-service account operations")
class UserController {

    private final RegisterUser registerUser;
    private final GetUser getUser;

    UserController(RegisterUser registerUser, GetUser getUser) {
        this.registerUser = registerUser;
        this.getUser = getUser;
    }

    @PostMapping
    @SecurityRequirements // public: no token needed
    @Operation(summary = "Create an account",
            description = "Public. Rate-limited per IP. New accounts get the CUSTOMER role only.")
    ResponseEntity<UserView> register(@Valid @RequestBody RegisterUserRequest request) {
        UserView user = registerUser.handle(new RegisterUserCommand(request.email(), request.password()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/users/{id}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(user);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's account", description = "Reads the user id from the token's sub claim.")
    UserView me(@AuthenticationPrincipal Jwt jwt) {
        // Only tokens issued to a signed-in person carry "roles". A service token (client credentials)
        // has no account behind it, so it gets a clear 403 instead of a confusing "invalid user id".
        if (!jwt.hasClaim("roles")) {
            throw new AccessDeniedException("This endpoint needs a user token, not a service token");
        }
        return getUser.handle(new UserId(jwt.getSubject()));
    }
}
