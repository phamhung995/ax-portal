package com.skillmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUT /users/me/password (Settings SRS §5.5).
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {
}
