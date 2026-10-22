package com.skillmanagement.user.dto;

import com.skillmanagement.auth.dto.OrgRef;

import java.time.Instant;
import java.util.List;

/**
 * User Detail payload (SRS §7). Email is immutable; no password/audit fields.
 * orgs = the user's Employed=Y org rows from organization_master.
 */
public record UserDetailResponse(
        Long userId,
        String userName,
        String email,
        List<OrgRef> orgs,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
