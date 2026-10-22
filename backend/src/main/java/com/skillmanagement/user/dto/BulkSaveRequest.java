package com.skillmanagement.user.dto;

import java.util.List;

/**
 * Payload for persisting bulk upload SUCCESS rows.
 * The client sends back the SUCCESS rows from the validation step.
 */
public record BulkSaveRequest(List<BulkRowResult> rows) {
}
