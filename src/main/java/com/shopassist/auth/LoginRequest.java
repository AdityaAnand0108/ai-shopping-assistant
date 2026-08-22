package com.shopassist.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-in payload. Constraints here are only about bounding input size; the
 * rules that govern a valid password belong to signup, and applying them here
 * would tell an attacker which guesses were not even worth checking.
 */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        @Size(max = 50)
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 200)
        String password
) {
}
