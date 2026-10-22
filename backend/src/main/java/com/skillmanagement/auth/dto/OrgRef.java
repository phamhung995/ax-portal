package com.skillmanagement.auth.dto;

import com.skillmanagement.org.model.OrganizationMaster;

import java.util.List;

/**
 * One organization assignment of a user (Employed=Y rows from organization_master).
 * part = the "Part" column, group = the "Group" column.
 */
public record OrgRef(
        String part,
        String group
) {
    public static List<OrgRef> fromOrgRows(List<OrganizationMaster> rows) {
        return rows.stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .map(o -> new OrgRef(o.getPart(), o.getGroupName()))
                .toList();
    }
}
