package com.furelise.skillmanagement.mcp.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Storage for MCP files (zip, json, yaml, txt, md, py, js, ts, 7z).
 * Files are stored on disk under the MCP upload directory and served
 * through the authenticated download API (never statically).
 */
public interface McpFileStorageService {

    /**
     * Validates and stores an MCP file.
     *
     * @return the relative file path (mcp-dir-relative) to persist in mcp_files.file_path
     * @throws com.furelise.skillmanagement.exception.BusinessException on
     *         invalid extension or size > 10MB
     */
    String store(Long mcpId, MultipartFile file);

    /** Deletes a previously stored MCP file (no-op if path unknown/missing). */
    void delete(String relativePath);

    /** Opens a stored MCP file for streaming to the client. */
    InputStream load(String relativePath);
}
