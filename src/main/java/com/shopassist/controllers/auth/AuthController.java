package com.shopassist.controllers.auth;

import com.shopassist.dto.auth.AuthResponse;
import com.shopassist.dto.auth.LoginRequest;
import com.shopassist.dto.auth.SignupRequest;
import com.shopassist.dto.auth.UserProfileResponse;
import com.shopassist.services.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and sign-in endpoints.
 *
 * <p>Signup and login are the only unauthenticated POST routes in the
 * application. The {@code @Operation} summaries below document the API for its
 * consumers; the rules that make these endpoints safe to expose — uniform
 * failures, lockout, hashing — live in {@link AuthService}.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration and sign-in")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "Register a new account and sign in immediately")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange username and password for an access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the signed-in account")
    public UserProfileResponse me() {
        return authService.currentProfile();
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the access token used for this request")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }
}
