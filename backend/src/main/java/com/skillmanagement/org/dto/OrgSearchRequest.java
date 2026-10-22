package com.skillmanagement.org.dto;

/**
 * Organization Management search criteria: all fields optional, AND between fields.
 */
public record OrgSearchRequest(
        String knoxId,
        String userName,
        String company,
        String part,
        String group,
        String employed,
        Integer page,
        Integer pageSize
) {
}
