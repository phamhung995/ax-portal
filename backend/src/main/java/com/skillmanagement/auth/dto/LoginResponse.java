package com.skillmanagement.auth.dto;

import com.skillmanagement.user.model.User;

import java.util.List;

/**
 * Login response payload (SRS §24): user identity + role + org assignments.
 * Tokens are carried in the envelope data alongside.
 */
public record LoginResponse(
        Long userId,
        String userName,
        String email,
        String role,
        List<OrgRef> orgs,
        String avatarUrl,
        String accessToken,
        String refreshToken
) {
    public static LoginResponse from(User user, List<OrgRef> orgs, String accessToken, String refreshToken) {
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getName() : null,
                orgs,
                user.getAvatarUrl(),
                accessToken,
                refreshToken
        );
    }
}
