package com.furelise.skillmanagement.mcp.dto;

import java.io.InputStream;

/**
 * A snapshotted version file ready to stream to the client.
 */
public record McpVersionFileDownload(
        String fileName,
        InputStream stream
) {}
