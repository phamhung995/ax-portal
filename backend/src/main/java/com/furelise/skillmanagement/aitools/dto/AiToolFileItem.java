package com.furelise.skillmanagement.aitools.dto;

import java.time.Instant;

/**
 * Metadata of the file attached to an AI tool.
 */
public record AiToolFileItem(
        Long id,
        String fileName,
        long fileSize,
        Instant createdAt
) {
}
