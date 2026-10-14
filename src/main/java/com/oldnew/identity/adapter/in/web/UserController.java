package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.application.GetUser;
import com.oldnew.identity.application.UserView;
import com.oldnew.identity.domain.model.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** On both instances. Sign-up is {@link SignUpController}, on the Customer Identity only. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Self-service account operations")
class UserController {

    private final GetUser getUser;

    UserController(GetUser getUser) {
        this.getUser = getUser;
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
