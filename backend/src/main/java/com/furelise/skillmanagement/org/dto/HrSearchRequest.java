package com.furelise.skillmanagement.org.dto;

/**
 * Search criteria for the Human Resource tab grid. All filters are optional
 * case-insensitive "contains".
 */
public record HrSearchRequest(
        String knoxId,
        String userName,
        String part,
        String systemKey,
        String role,
        Integer page,
        Integer pageSize
) {
}
