package com.skillmanagement.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for self-registration (Login SRS v1.6): name, email, password.
 * Requires a verified EMAIL_VERIFICATION OTP before account creation.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String password,
        @NotBlank String confirmPassword
) {
}
