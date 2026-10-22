package com.skillmanagement.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create User (SRS §5): only User Name, Email, Password, Confirm Password
 * are editable. Role = NORMAL_USER and Status = ACTIVE are fixed defaults.
 * Email is entered once and immutable afterwards.
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String userName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String password,
        @NotBlank String confirmPassword
) {
}
