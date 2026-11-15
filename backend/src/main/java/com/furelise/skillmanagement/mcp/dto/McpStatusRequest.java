package com.furelise.skillmanagement.mcp.dto;

/**
 * Request body for status transitions (submit / approve / reject / withdraw).
 */
public record McpStatusRequest(String approvalStatus, String rejectionReason) {
}
