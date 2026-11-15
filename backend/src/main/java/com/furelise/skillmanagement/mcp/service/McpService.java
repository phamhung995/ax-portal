package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.*;
import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import com.furelise.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * Core MCP Management operations (MCP Management SRS — rebuilt on the Skill
 * model, PUBLIC only): create, search (visibility-aware), detail.
 */
public interface McpService {

    /**
     * Creates an MCP in DRAFT status.
     *
     * @param files one or more attached files (required)
     * @return the persisted MCP id
     */
    Long create(String name, String description, String hashtagsJson,
                List<Long> categoryIds, List<Long> stageIds,
                MultipartFile[] files, User creator);

    /**
     * Searches the MCP grid for the given viewer (visibility enforced).
     */
    McpSearchResult search(McpSearchRequest request, User viewer);

    /**
     * MCP detail for the Detail popup.
     * Returns 404 MCP_NOT_FOUND when the MCP is not visible to the viewer.
     */
    McpDetailResponse getDetail(Long mcpId, User viewer);

    /**
     * Pre-delete validation: which of the selected MCPs cannot be deleted
     * (not creator and not admin). All-or-nothing semantics apply on delete.
     */
    McpDeleteCheckResponse checkDelete(List<Long> ids, User user);

    /**
     * Bulk soft delete (all-or-nothing): if any selected MCP is not
     * deletable, nothing is deleted and 403 MCP_DELETE_FORBIDDEN is thrown.
     */
    void deleteBatch(List<Long> ids, User user);

    /**
     * Idempotent view count: one user counts once per MCP per view window.
     *
     * @return the current view count
     */
    int incrementView(Long mcpId, User viewer);

    /**
     * Records a public download event (single file or "Download All" zip,
     * current or snapshotted version).
     */
    void recordDownload(Long mcpId, User user, boolean downloadAll);

    /**
     * Exports the search result (visibility + criteria, no paging) to an
     * in-memory .xlsx (NASC-safe).
     *
     * @param selectedIds optional: export only these ids
     * @return the workbook bytes
     */
    byte[] exportExcel(McpSearchRequest request, List<Long> selectedIds, User viewer);

    /**
     * Updates an MCP (Edit). Enforces edit permission, optimistic lock
     * (updatedAt), and the APPROVED+non-admin name read-only rule.
     *
     * <p>Files: for DRAFT/REJECTED MCPs new files are appended and
     * {@code removeFileIds} removes live files (at least one file must
     * remain). For APPROVED MCPs both are IGNORED — files are only managed
     * through the version-update flow.
     *
     * @return the current version number
     */
    int update(Long mcpId, String name, String description,
               String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
               MultipartFile[] files, List<Long> removeFileIds,
               Instant expectedUpdatedAt, User user);

    /** Deletes one attached file of an MCP (204 / 404 MCP_FILE_NOT_FOUND). */
    void deleteFile(Long mcpId, Long fileId, User user);

    /**
     * Submits a version update for an APPROVED MCP (owner or admin):
     * stages the new metadata + files (pending) and moves the MCP to
     * PENDING_APPROVAL. The live version stays available until an admin
     * approves the update.
     */
    void updateVersion(Long mcpId, String name, String description,
                       String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                       MultipartFile[] files, List<Long> removeFileIds,
                       User user);

    /**
     * Cancels a pending version update (owner or admin): discards the
     * staged metadata + pending files and returns the MCP to APPROVED.
     */
    void cancelVersionUpdate(Long mcpId, User user);

    /** List of superseded (snapshot) versions, newest first. */
    List<McpVersionItem> listVersions(Long mcpId, User viewer);

    /** Streams a file of a snapshotted version (visibility enforced). */
    McpVersionFileDownload downloadVersionFile(Long mcpId, int version, Long fileId, User viewer);

    /** All files of a snapshotted version (visibility enforced). */
    List<McpVersionFile> listVersionFiles(Long mcpId, int version, User viewer);

    /** Subscribes to a published MCP (owner cannot subscribe own MCP). */
    void subscribe(Long mcpId, User user);

    /** Unsubscribes from an MCP. */
    void unsubscribe(Long mcpId, User user);
}
