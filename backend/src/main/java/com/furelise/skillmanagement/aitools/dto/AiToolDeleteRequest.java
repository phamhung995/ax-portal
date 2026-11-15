package com.furelise.skillmanagement.aitools.dto;

import java.util.List;

/**
 * Bulk delete request for AI tools.
 */
public record AiToolDeleteRequest(List<Long> ids) {
}
