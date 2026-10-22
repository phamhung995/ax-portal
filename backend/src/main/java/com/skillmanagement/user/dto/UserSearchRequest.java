package com.skillmanagement.user.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * User Management search criteria (SRS §4):
 * all fields optional; AND between fields, IN/OR within a field.
 */
public record UserSearchRequest(
        String userName,
        String email,
        String part,
        String group,
        String organization,
        List<String> roles,
        List<String> statuses,
        LocalDate createdDateFrom,
        LocalDate createdDateTo,
        List<Long> ids,
        Integer page,
        Integer pageSize,
        String sortBy,
        String sortDirection
) {
}
