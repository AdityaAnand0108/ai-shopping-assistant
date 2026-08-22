package com.shopassist.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload.
 *
 * <p>The password ceiling is 72 characters because BCrypt silently ignores
 * everything past 72 bytes; rejecting longer input is honest, where accepting it
 * would quietly weaken the hash.
 */
public record SignupRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may contain only letters, digits, dots, underscores and hyphens")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one digit")
        String password,

        @Size(max = 120, message = "Name must be at most 120 characters")
        String fullName
) {
}
