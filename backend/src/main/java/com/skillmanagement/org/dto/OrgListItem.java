package com.skillmanagement.org.dto;

import java.time.Instant;

/**
 * One organization row for the grid.
 * id = composite PK joined by "|" (row key).
 * matchedUserEmail is null when no user's Knox ID matches the row's Knox ID.
 */
public record OrgListItem(
        String id,
        String knoxId,
        String userName,
        String employed,
        String regularPartner,
        String company,
        String part,
        String group,
        String employeeNumber,
        Instant importedAt,
        String matchedUserEmail
) {
}
