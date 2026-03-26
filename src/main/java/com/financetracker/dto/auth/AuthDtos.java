package com.financetracker.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ── Register ──────────────────────────────────────────────────────────────────

public class AuthDtos {

    public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @Email(message = "Must be a valid email")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
    ) {}

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
    ) {}

    public record RefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
    ) {}

    public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,   // seconds
        UserSummary user
    ) {
        public static AuthResponse of(String access, String refresh, long expiresIn, UserSummary user) {
            return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
        }
    }

    public record UserSummary(Long id, String email, String fullName, String role) {}
}
