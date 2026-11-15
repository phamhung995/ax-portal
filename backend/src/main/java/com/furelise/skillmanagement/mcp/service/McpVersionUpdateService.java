package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
import com.furelise.skillmanagement.mcp.model.McpCategoryMapping;
import com.furelise.skillmanagement.mcp.model.McpFile;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpStageMapping;
import com.furelise.skillmanagement.mcp.model.McpSubscription;
import com.furelise.skillmanagement.mcp.model.McpVersion;
import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import com.furelise.skillmanagement.mcp.repository.McpCategoryMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpStageMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpSubscriptionRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionRepository;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Version-update mechanics for APPROVED MCPs.
 *
 * <p>A version update is staged on the MCP (pendingMetadata +
 * pendingRemoveFileIds + files with pending_yn='Y'). This service either
 * commits the staged state (approval) or discards it (reject/cancel):
 *
 * <ul>
 *   <li>{@link #completeVersionUpdate} — snapshots the CURRENT (superseded)
 *       state into mcp_version/mcp_version_file, applies the pending
 *       metadata + files, bumps the version, notifies subscribers.</li>
 *   <li>{@link #discardPendingVersion} — deletes the pending files (disk +
 *       rows) and clears the staged fields.</li>
 * </ul>
 *
 * <p>Files removed from the live MCP are NOT deleted from disk — old
 * versions keep them for download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpVersionUpdateService {

    private final McpMasterRepository mcpMasterRepository;
    private final McpFileRepository mcpFileRepository;
    private final McpVersionRepository mcpVersionRepository;
    private final McpVersionFileRepository mcpVersionFileRepository;
    private final McpCategoryMappingRepository categoryMappingRepository;
    private final McpStageMappingRepository stageMappingRepository;
    private final McpSubscriptionRepository subscriptionRepository;
    private final McpFileStorageService mcpFileStorageService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;

    /** Public frontend base URL — appended to MCP links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    /**
     * Commits a pending version update: snapshot the superseded state,
     * apply the staged metadata/files, bump the version.
     * The MCP's status is already set by the caller (workflow).
     */
    @Transactional
    public void completeVersionUpdate(McpMaster mcp, User actor) {
        McpPendingMetadata pending = mcp.getPendingMetadata();
        List<Long> removeIds = mcp.getPendingRemoveFileIds() != null
                ? mcp.getPendingRemoveFileIds() : List.of();

        // 1. Snapshot the CURRENT (superseded) version. At this point the
        //    files being removed are still live (pending_yn='N'), so the
        //    live-file list already covers the whole old version.
        List<McpFile> liveFiles = mcpFileRepository.findByMcpIdAndPendingYn(mcp.getId(), "N");
        List<McpFile> removedFiles = removeIds.isEmpty()
                ? List.of()
                : mcpFileRepository.findAllById(removeIds).stream()
                        .filter(f -> f.getMcpId().equals(mcp.getId()))
                        .toList();

        McpVersion snapshot = mcpVersionRepository.save(McpVersion.builder()
                .mcpId(mcp.getId())
                .version(mcp.getVersion())
                .name(mcp.getMcpName())
                .description(mcp.getDescription())
                .hashtags(mcp.getHashtags())
                .createdBy(mcp.getCreatedBy())
                .createdAt(mcp.getUpdatedAt() != null ? mcp.getUpdatedAt() : Instant.now())
                .build());
        for (McpFile f : liveFiles) {
            mcpVersionFileRepository.save(McpVersionFile.builder()
                    .mcpVersionId(snapshot.getId())
                    .fileName(f.getFileName())
                    .filePath(f.getFilePath())
                    .fileSize(f.getFileSize())
                    .build());
        }

        // 2. Apply the staged metadata.
        mcp.setMcpName(pending.name());
        mcp.setDescription(pending.description());
        mcp.setHashtags(pending.hashtags());
        storeCategoryMappings(mcp.getId(), pending.categoryIds());
        storeStageMappings(mcp.getId(), pending.stageIds());

        // 3. Promote pending files to live.
        for (McpFile f : mcpFileRepository.findByMcpIdAndPendingYn(mcp.getId(), "Y")) {
            f.setPendingYn("N");
            mcpFileRepository.save(f);
        }

        // 4. Remove the selected live files (DB rows only — disk kept for
        //    the version snapshot).
        for (McpFile f : removedFiles) {
            mcpFileRepository.delete(f);
        }

        // 5. Bump version, clear staged state.
        mcp.setVersion(mcp.getVersion() + 1);
        mcp.setPendingMetadata(null);
        mcp.setPendingRemoveFileIds(null);
        mcp.setRejectionReason(null);
        mcp.setUpdatedAt(Instant.now());
        mcpMasterRepository.save(mcp);

        notifySubscribers(mcp, actor);
    }

    /**
     * Discards a pending version update (reject or cancel): deletes the
     * pending files from disk + DB and clears the staged fields.
     * The MCP's status is set by the caller.
     */
    @Transactional
    public void discardPendingVersion(McpMaster mcp) {
        for (McpFile f : mcpFileRepository.findByMcpIdAndPendingYn(mcp.getId(), "Y")) {
            try {
                mcpFileStorageService.delete(f.getFilePath());
            } catch (Exception e) {
                log.warn("Không thể xóa file pending {} của MCP {}: {}",
                        f.getFilePath(), mcp.getId(), e.getMessage());
            }
            mcpFileRepository.delete(f);
        }
        mcp.setPendingMetadata(null);
        mcp.setPendingRemoveFileIds(null);
        mcp.setUpdatedAt(Instant.now());
        mcpMasterRepository.save(mcp);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void storeCategoryMappings(Long mcpId, List<Long> categoryIds) {
        categoryMappingRepository.deleteByMcpId(mcpId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : categoryIds) {
            categoryMappingRepository.save(McpCategoryMapping.builder()
                    .mcpId(mcpId).categoryId(categoryId).build());
        }
    }

    private void storeStageMappings(Long mcpId, List<Long> stageIds) {
        stageMappingRepository.deleteByMcpId(mcpId);
        if (stageIds == null || stageIds.isEmpty()) {
            return;
        }
        for (Long stageId : stageIds) {
            stageMappingRepository.save(McpStageMapping.builder()
                    .mcpId(mcpId).stageId(stageId).build());
        }
    }

    /** In-app + email notification to all subscribers (and always the owner). */
    private void notifySubscribers(McpMaster mcp, User actor) {
        List<McpSubscription> subs = subscriptionRepository.findByMcpId(mcp.getId());
        List<User> subscribers = subs.isEmpty() ? List.of() : userRepository.findAllById(
                subs.stream().map(McpSubscription::getUserId).toList());
        // The owner is always notified (in addition to their subscribers),
        // deduped when the owner is also a subscriber.
        List<User> recipients = new ArrayList<>(subscribers);
        if (subscribers.stream().noneMatch(u -> u.getId().equals(mcp.getCreatedBy()))) {
            User owner = userRepository.findById(mcp.getCreatedBy()).orElse(null);
            if (owner != null) {
                recipients.add(owner);
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        String title = "MCP \"" + mcp.getMcpName() + "\" has a new version.";
        String mcpUrl = mcpDetailUrl(mcp.getId());
        String subscriberBody = "The MCP \"" + mcp.getMcpName() + "\" you are subscribed to has a new version."
                + (mcpUrl != null ? "\n\n" + mcpUrl : "");
        String ownerBody = "Your MCP \"" + mcp.getMcpName() + "\" has a new version."
                + (mcpUrl != null ? "\n\n" + mcpUrl : "");
        for (User sub : recipients) {
            UserSettings settings = userSettingsRepository.findById(sub.getId()).orElse(null);
            boolean isOwner = sub.getId().equals(mcp.getCreatedBy());
            eventNotificationService.notify(actor, sub, settings,
                    new EventNotificationService.EventDetails(
                            title,
                            new EventNotificationService.EventContext(
                                    String.valueOf(mcp.getId()), null, null, null, null),
                            "[AX Portal] " + title,
                            isOwner ? ownerBody : subscriberBody));
        }
    }

    /** Frontend URL that opens the MCP detail popup (?detail={id}). */
    private String mcpDetailUrl(Long mcpId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/mcps?detail=" + mcpId;
    }
}
