package com.oldnew.identity.adapter.in.web;

import com.oldnew.identity.application.RegisterUser;
import com.oldnew.identity.application.RegisterUserCommand;
import com.oldnew.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Self sign-up. Customer Identity only (ADR-0009): the Staff Identity has no such endpoint at all. */
@RestController
@Profile("customer")
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Self-service account operations")
class SignUpController {

    private final RegisterUser registerUser;

    SignUpController(RegisterUser registerUser) {
        this.registerUser = registerUser;
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
}
