package com.skillmanagement.skill.service;

import com.skillmanagement.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Storage for skill files (zip, json, yaml, txt, md, py, js, ts, 7z).
 * Files are stored on disk under the skill upload directory and served
 * through the authenticated download API (never statically).
 */
public interface SkillFileStorageService {

    /**
     * Validates and stores a skill file.
     *
     * @return the relative file path (skill-dir-relative) to persist in skills_master.file_path
     * @throws BusinessException on
     *         invalid extension or size > 10MB
     */
    String store(Long skillId, MultipartFile file);

    /** Deletes a previously stored skill file (no-op if path unknown/missing). */
    void delete(String relativePath);

    /** Opens a stored skill file for streaming to the client. */
    InputStream load(String relativePath);
}
