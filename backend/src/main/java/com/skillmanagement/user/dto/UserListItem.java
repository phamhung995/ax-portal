package com.skillmanagement.user.dto;

import com.skillmanagement.auth.dto.OrgRef;

import java.time.Instant;
import java.util.List;

/**
 * User row for the User Management grid (SRS §4.4). No password, no audit fields.
 * orgs = the user's Employed=Y org rows from organization_master.
 */
public record UserListItem(
        Long userId,
        String userName,
        String email,
        List<OrgRef> orgs,
        String role,
        String status,
        Instant createdAt
) {
}
