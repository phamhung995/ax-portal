package com.skillmanagement.user.dto;

/**
 * Bulk upload validation result for one Excel row (SRS §6.4).
 * Row order must match the source file.
 */
public record BulkRowResult(
        int rowNumber,
        String userName,
        String email,
        String password,
        String status, // SUCCESS / ERROR
        String errors
) {
    public static BulkRowResult success(int row, String userName, String email, String password) {
        return new BulkRowResult(row, userName, email, password, "SUCCESS", null);
    }

    public static BulkRowResult error(int row, String userName, String email, String password, String errors) {
        return new BulkRowResult(row, userName, email, password, "ERROR", errors);
    }
}
