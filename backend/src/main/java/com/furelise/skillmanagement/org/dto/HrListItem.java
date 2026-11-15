package com.furelise.skillmanagement.org.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row of the Human Resource tab grid: a person's assignment to one
 * system, with the monthly input values.
 */
public record HrListItem(
        String knoxId,
        String userName,
        String employed,
        String regularPartner,
        String company,
        String part,
        String employeeNumber,
        String systemKey,
        String systemCode,
        String role,
        BigDecimal jan,
        BigDecimal feb,
        BigDecimal mar,
        BigDecimal apr,
        BigDecimal may,
        BigDecimal jun,
        BigDecimal jul,
        BigDecimal aug,
        BigDecimal sep,
        BigDecimal oct,
        BigDecimal nov,
        BigDecimal dec,
        String projectKey,
        String projectName
) {
}
