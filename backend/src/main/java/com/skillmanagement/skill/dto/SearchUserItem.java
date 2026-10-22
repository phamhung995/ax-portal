package com.skillmanagement.skill.dto;

import com.skillmanagement.auth.dto.OrgRef;

import java.util.List;

/**
 * One row of the Search User popup (grid Creator field, SRS §7.4).
 * orgs = the user's organization rows (Employed=Y), shown as "Part > Group".
 */
public record SearchUserItem(
        Long id,
        String name,
        String knoxId,
        List<OrgRef> orgs
) {
}
