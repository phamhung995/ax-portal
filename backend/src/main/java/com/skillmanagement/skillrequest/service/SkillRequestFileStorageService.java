package com.skillmanagement.skillrequest.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Disk storage for skill request attachments under
 * {@code uploads/skill-requests/{requestId}/}.
 * Stored file names are UUID-prefixed to avoid name collisions and path
 * traversal; the original file name is kept in skill_request_files.file_name.
 */
public interface SkillRequestFileStorageService {

    /**
     * Stores one attachment.
     *
     * @return the request-dir-relative path ({@code {requestId}/{storedName}})
     */
    String store(Long requestId, MultipartFile file);

    /** Deletes a stored file by request-dir-relative path (no-op when missing). */
    void delete(String relativePath);

    /** Opens a stored file by request-dir-relative path. */
    InputStream load(String relativePath);

    /** True when the file name has an image extension. */
    boolean isImage(String fileName);
}
