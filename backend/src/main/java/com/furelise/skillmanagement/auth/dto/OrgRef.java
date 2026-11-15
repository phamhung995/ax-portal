package com.furelise.skillmanagement.auth.dto;

import com.furelise.skillmanagement.org.model.OrganizationMaster;

import java.util.List;

/**
 * One organization assignment of a user (Employed=Y row from organization_master).
 * Hierarchy: Division &gt; Center &gt; Group &gt; Part. A person's row stops at
 * the highest level they have (lower levels are null).
 */
public record OrgRef(
        String part,
        String group,
        String center,
        String division,
        String position
) {
    public static List<OrgRef> fromOrgRows(List<OrganizationMaster> rows) {
        return rows.stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .map(OrgRef::fromOrgRow)
                .toList();
    }

    public static OrgRef fromOrgRow(OrganizationMaster o) {
        return new OrgRef(o.getPart(), o.getGroupName(), o.getCenter(),
                o.getDivision(), o.getPosition());
    }
}
