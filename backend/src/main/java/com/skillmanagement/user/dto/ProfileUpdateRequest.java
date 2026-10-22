package com.skillmanagement.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/me/profile (Settings SRS §5.2).
 * Email is immutable (decision: Login SRS v1.6) — not part of the payload.
 */
public record ProfileUpdateRequest(
        @NotBlank @Size(max = 50) String displayName
) {
}
