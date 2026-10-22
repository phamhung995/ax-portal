package com.skillmanagement.skill.service;

import com.skillmanagement.skill.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.skill.model.SkillVersionFile;
import com.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * Core Skill Management operations (Skill Management SRS):
 * create, search (visibility-aware), detail.
 */
public interface SkillService {

    /**
     * Creates a skill in DRAFT status.
     *
     * @param files zero or more attached files
     * @return the persisted skill id
     */
    Long create(String name, String description, String publicScope,
                String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                MultipartFile[] files, User creator);

    /**
     * Searches the skill grid for the given viewer (visibility enforced).
     */
    SkillSearchResult search(SkillSearchRequest request, User viewer);

    /**
     * Skill detail for the Detail popup.
     * Returns 404 SKILL_NOT_FOUND when the skill is not visible to the viewer.
     */
    SkillDetailResponse getDetail(Long skillId, User viewer);

    /**
     * Pre-delete validation: which of the selected skills cannot be deleted
     * (not creator and not admin). All-or-nothing semantics apply on delete.
     */
    SkillDeleteCheckResponse checkDelete(List<Long> ids, User user);

    /**
     * Bulk soft delete (all-or-nothing): if any selected skill is not
     * deletable, nothing is deleted and 403 SKILL_DELETE_FORBIDDEN is thrown
     * with the denied list in the message. Otherwise each skill is soft
     * deleted (deleted_yn='Y'), its file removed from disk and its
     * subscriptions removed.
     */
    void deleteBatch(List<Long> ids, User user);

    /**
     * Idempotent view count: one user counts once per skill per view window.
     *
     * @return the current view count
     */
    int incrementView(Long skillId, User viewer);

    /**
     * Exports the search result (visibility + criteria, no paging) to an
     * in-memory .xlsx (NASC-safe).
     *
     * @param selectedIds optional: export only these ids (intersection with
     *                    the search result)
     * @return the workbook bytes
     */
    byte[] exportExcel(SkillSearchRequest request, List<Long> selectedIds, User viewer);

    /**
     * Updates a skill (Edit SRS). Enforces edit permission, optimistic lock
     * (updatedAt), and the APPROVED+non-admin name read-only rule.
     *
     * <p>Files: for DRAFT/REJECTED skills new files are appended (individual
     * removal via deleteFile). For APPROVED skills the file list is IGNORED —
     * files are only managed through the version-update flow.
     *
     * <p>The version number is NOT bumped here; it only advances when a
     * version update is approved.
     *
     * @return the current version number
     */
    int update(Long skillId, String name, String description, String publicScope,
               String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
               MultipartFile[] files,
               Instant expectedUpdatedAt, User user);

    /** Deletes one attached file of a skill (204 / 404 FILE_NOT_FOUND). */
    void deleteFile(Long skillId, Long fileId, User user);

    /**
     * Submits a version update for an APPROVED skill (creator or admin):
     * stages the new metadata + files (pending) and moves the skill to
     * PENDING_APPROVAL. The live version stays available until an admin
     * approves the update.
     */
    void updateVersion(Long skillId, String name, String description, String publicScope,
                       String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                       MultipartFile[] files, List<Long> removeFileIds, User user);

    /**
     * Cancels a pending version update (creator or admin): discards the
     * staged metadata + pending files and returns the skill to APPROVED.
     */
    void cancelVersionUpdate(Long skillId, User user);

    /** List of superseded (snapshot) versions, newest first. */
    List<SkillVersionItem> listVersions(Long skillId, User viewer);

    /** Streams a file of a snapshotted version (visibility enforced). */
    SkillVersionFileDownload downloadVersionFile(Long skillId, int version, Long fileId, User viewer);

    /** All files of a snapshotted version (visibility enforced). */
    List<SkillVersionFile> listVersionFiles(Long skillId, int version, User viewer);

    /** Subscribes to a PUBLISHED skill (creator cannot subscribe own skill). */
    void subscribe(Long skillId, User user);

    /** Unsubscribes from a skill. */
    void unsubscribe(Long skillId, User user);
}
