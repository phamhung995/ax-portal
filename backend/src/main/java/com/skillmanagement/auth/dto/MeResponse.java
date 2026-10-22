package com.skillmanagement.auth.dto;

import java.util.List;

/**
 * GET /users/me payload (Settings SRS §5.1).
 */
public record MeResponse(
        Long id,
        String displayName,
        String email,
        String avatarPath,
        List<OrgRef> orgs,
        SettingsBlock settings
) {
    public record SettingsBlock(
            String language,
            String timezone,
            String dateFormat,
            boolean emailNotify,
            boolean inappNotify
    ) {
    }
}
