package com.furelise.skillmanagement.aitools.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Storage for AI tool files (one file per tool).
 * Files are stored on disk under the AI tool upload directory and served
 * through the authenticated download API (never statically).
 */
public interface AiToolFileStorageService {

    /**
     * Validates and stores an AI tool file.
     *
     * @return the relative file path (ai-tool-dir-relative) to persist
     * @throws com.furelise.skillmanagement.exception.BusinessException on
     *         invalid extension or size > 50MB
     */
    String store(Long toolId, MultipartFile file);

    /** Deletes a previously stored AI tool file (no-op if path unknown/missing). */
    void delete(String relativePath);

    /** Opens a stored AI tool file for streaming to the client. */
    InputStream load(String relativePath);
}
