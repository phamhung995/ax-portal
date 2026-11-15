package com.furelise.skillmanagement.aitools.service;

import com.furelise.skillmanagement.aitools.dto.AiToolDetailResponse;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchRequest;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchResult;
import com.furelise.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Core AI Tools operations: create (any authenticated user), search (all
 * users), detail, update (uploader or admin), delete (uploader or admin),
 * download (all users).
 */
public interface AiToolService {

    /**
     * Creates an AI tool (any authenticated user).
     *
     * @return the persisted tool id
     */
    Long create(String name, String description, String tagsJson,
                List<Long> categoryIds, List<Long> stageIds,
                MultipartFile file, User creator);

    /** Searches the AI tools grid (paged). */
    AiToolSearchResult search(AiToolSearchRequest request, User viewer);

    /**
     * AI tool detail for the detail popup.
     * Returns 404 AI_TOOL_NOT_FOUND when the tool does not exist or is deleted.
     */
    AiToolDetailResponse getDetail(Long toolId, User viewer);

    /**
     * Updates an AI tool (uploader or admin). A new file replaces the old
     * one; {@code removeFile} deletes the attached file (a new file, when
     * provided, is stored instead).
     */
    void update(Long toolId, String name, String description, String tagsJson,
                List<Long> categoryIds, List<Long> stageIds,
                boolean removeFile, MultipartFile file, User user);

    /**
     * Bulk delete (uploader or admin, per tool): soft delete, remove mappings
     * and files from disk. Tools the user may not edit are skipped, not
     * failed — see {@link AiToolDeleteResult}.
     */
    AiToolDeleteResult deleteBatch(List<Long> ids, User user);

    /**
     * Streams the attached file of a tool and records the download event.
     *
     * @return the file name (for the Content-Disposition header)
     */
    AiToolDownloadResult download(Long toolId, Long fileId, User user);

    /** A stored file to stream: original name + open stream. */
    record AiToolDownloadResult(String fileName, InputStream stream) {
    }

    /** Bulk delete outcome: how many were deleted + names of skipped tools. */
    record AiToolDeleteResult(int deleted, List<String> skippedNames) {
    }
}
