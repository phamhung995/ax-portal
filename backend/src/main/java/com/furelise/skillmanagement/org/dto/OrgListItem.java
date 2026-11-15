package com.furelise.skillmanagement.org.dto;

import java.time.Instant;

/**
 * One organization row for the grid (one person = one row, knox_id PK).
 * Hierarchy: Division > Center > Group > Part (lower levels may be null).
 * matchedUserEmail is null when no user's Knox ID matches the row's Knox ID.
 */
public record OrgListItem(
        String id,
        String knoxId,
        String userName,
        String employed,
        String regularPartner,
        String company,
        String division,
        String center,
        String group,
        String part,
        String employeeNumber,
        String phone,
        String email,
        String position,
        Instant importedAt,
        String matchedUserEmail
) {
}
