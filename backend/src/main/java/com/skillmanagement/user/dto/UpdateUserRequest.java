package com.skillmanagement.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Update User (User Detail, SRS §8): User Name, Role, Status only.
 * Email is immutable (EMAIL_IMMUTABLE). Self role/status changes are rejected.
 * expectedVersion carries the optimistic-lock version read by the client.
 */
public record UpdateUserRequest(
        @Size(max = 100) String userName,
        String role,
        String status,
        Long expectedVersion
) {
}
