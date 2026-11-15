package com.furelise.skillmanagement.org.dto;

/**
 * Organization Management search criteria: all fields optional, AND between fields.
 */
public record OrgSearchRequest(
        String knoxId,
        String userName,
        String company,
        String division,
        String center,
        String group,
        String part,
        String employed,
        Integer page,
        Integer pageSize
) {
}
