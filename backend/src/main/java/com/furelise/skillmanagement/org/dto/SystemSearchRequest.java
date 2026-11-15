package com.furelise.skillmanagement.org.dto;

/**
 * Search criteria for the Systems tab grid. All filters are optional
 * case-insensitive "contains" (exact match for phase).
 */
public record SystemSearchRequest(
        String systemKey,
        String systemName,
        String part,
        String phase,
        Integer page,
        Integer pageSize
) {
}
