package com.skillmanagement.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {
}
