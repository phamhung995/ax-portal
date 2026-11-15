package com.furelise.skillmanagement.mcp.dto;

import java.io.InputStream;

/**
 * Payload for file download. Carries the stream + metadata so the controller
 * can set proper headers without leaking the storage path.
 */
public record McpDownloadPayload(
        InputStream stream,
        String fileName,
        long fileSize,
        String contentType
) {
}
