package com.furelise.skillmanagement.skill.service;

import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.dto.SkillPendingMetadata;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillCategoryMapping;
import com.furelise.skillmanagement.skill.model.SkillFile;
import com.furelise.skillmanagement.skill.model.SkillStageMapping;
import com.furelise.skillmanagement.skill.model.SkillSubscription;
import com.furelise.skillmanagement.skill.model.SkillVersion;
import com.furelise.skillmanagement.skill.model.SkillVersionFile;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionRepository;
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
 * Version-update mechanics for APPROVED skills.
 *
 * <p>A version update is staged on the skill (pendingMetadata +
 * pendingRemoveFileIds + files with pending_yn='Y'). This service either
 * commits the staged state (approval) or discards it (reject/cancel):
 *
 * <ul>
 *   <li>{@link #completeVersionUpdate} — snapshots the CURRENT (superseded)
 *       state into skill_version/skill_version_file, applies the pending
 *       metadata + files, bumps the version, notifies subscribers.</li>
 *   <li>{@link #discardPendingVersion} — deletes the pending files (disk +
 *       rows) and clears the staged fields.</li>
 * </ul>
 *
 * <p>Files removed from the live skill are NOT deleted from disk — old
 * versions keep them for download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillVersionUpdateService {

    private final SkillRepository skillRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillVersionFileRepository skillVersionFileRepository;
    private final SkillCategoryMappingRepository categoryMappingRepository;
    private final SkillStageMappingRepository stageMappingRepository;
    private final SkillSubscriptionRepository subscriptionRepository;
    private final SkillFileStorageService skillFileStorageService;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;

    /** Public frontend base URL — appended to skill links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    /**
     * Commits a pending version update: snapshot the superseded state,
     * apply the staged metadata/files, bump the version.
     * The skill's status is already set by the caller (workflow).
     */
    @Transactional
    public void completeVersionUpdate(Skill skill, User actor) {
        SkillPendingMetadata pending = skill.getPendingMetadata();
        List<Long> removeIds = skill.getPendingRemoveFileIds() != null
                ? skill.getPendingRemoveFileIds() : List.of();

        // 1. Snapshot the CURRENT (superseded) version. At this point the
        //    files being removed are still live (pending_yn='N'), so the
        //    live-file list already covers the whole old version.
        List<SkillFile> liveFiles = skillFileRepository.findBySkillIdAndPendingYn(skill.getId(), "N");
        List<SkillFile> removedFiles = removeIds.isEmpty()
                ? List.of()
                : skillFileRepository.findAllById(removeIds).stream()
                        .filter(f -> f.getSkillId().equals(skill.getId()))
                        .toList();

        SkillVersion snapshot = skillVersionRepository.save(SkillVersion.builder()
                .skillId(skill.getId())
                .version(skill.getVersion())
                .name(skill.getName())
                .description(skill.getDescription())
                .publicScope(skill.getPublicScope())
                .systemKey(skill.getSystemKey())
                .subSystemCode(skill.getSubSystemCode())
                .hashtags(skill.getHashtags())
                .createdBy(skill.getCreatedBy())
                .createdAt(skill.getUpdatedAt() != null ? skill.getUpdatedAt() : Instant.now())
                .build());
        for (SkillFile f : liveFiles) {
            skillVersionFileRepository.save(SkillVersionFile.builder()
                    .skillVersionId(snapshot.getId())
                    .fileName(f.getFileName())
                    .filePath(f.getFilePath())
                    .fileSize(f.getFileSize())
                    .build());
        }

        // 2. Apply the staged metadata.
        skill.setName(pending.name());
        skill.setDescription(pending.description());
        skill.setPublicScope(pending.publicScope());
        skill.setSystemKey(pending.systemKey());
        skill.setSubSystemCode(pending.subSystemCode());
        skill.setHashtags(pending.hashtags());
        storeCategoryMappings(skill.getId(), pending.categoryIds());
        storeStageMappings(skill.getId(), pending.stageIds());

        // 3. Promote pending files to live.
        for (SkillFile f : skillFileRepository.findBySkillIdAndPendingYn(skill.getId(), "Y")) {
            f.setPendingYn("N");
            skillFileRepository.save(f);
        }

        // 4. Remove the selected live files (DB rows only — disk kept for
        //    the version snapshot).
        for (SkillFile f : removedFiles) {
            skillFileRepository.delete(f);
        }

        // 5. Bump version, clear staged state.
        skill.setVersion(skill.getVersion() + 1);
        skill.setPendingMetadata(null);
        skill.setPendingRemoveFileIds(null);
        skill.setRejectionReason(null);
        skill.setUpdatedAt(Instant.now());
        skillRepository.save(skill);

        notifySubscribers(skill, actor);
    }

    /**
     * Discards a pending version update (reject or cancel): deletes the
     * pending files from disk + DB and clears the staged fields.
     * The skill's status is set by the caller.
     */
    @Transactional
    public void discardPendingVersion(Skill skill) {
        for (SkillFile f : skillFileRepository.findBySkillIdAndPendingYn(skill.getId(), "Y")) {
            try {
                skillFileStorageService.delete(f.getFilePath());
            } catch (Exception e) {
                log.warn("Không thể xóa file pending {} của skill {}: {}",
                        f.getFilePath(), skill.getId(), e.getMessage());
            }
            skillFileRepository.delete(f);
        }
        skill.setPendingMetadata(null);
        skill.setPendingRemoveFileIds(null);
        skill.setUpdatedAt(Instant.now());
        skillRepository.save(skill);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void storeCategoryMappings(Long skillId, List<Long> categoryIds) {
        categoryMappingRepository.deleteBySkillId(skillId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : categoryIds) {
            categoryMappingRepository.save(SkillCategoryMapping.builder()
                    .skillId(skillId).categoryId(categoryId).build());
        }
    }

    private void storeStageMappings(Long skillId, List<Long> stageIds) {
        stageMappingRepository.deleteBySkillId(skillId);
        if (stageIds == null || stageIds.isEmpty()) {
            return;
        }
        for (Long stageId : stageIds) {
            stageMappingRepository.save(SkillStageMapping.builder()
                    .skillId(skillId).stageId(stageId).build());
        }
    }

    /** In-app + email notification to all subscribers (and always the creator). */
    private void notifySubscribers(Skill skill, User actor) {
        List<SkillSubscription> subs = subscriptionRepository.findBySkillId(skill.getId());
        List<User> subscribers = subs.isEmpty() ? List.of() : userRepository.findAllById(
                subs.stream().map(SkillSubscription::getUserId).toList());
        // The creator is always notified (in addition to their subscribers),
        // deduped when the creator is also a subscriber.
        List<User> recipients = new ArrayList<>(subscribers);
        if (subscribers.stream().noneMatch(u -> u.getId().equals(skill.getCreatedBy()))) {
            User creator = userRepository.findById(skill.getCreatedBy()).orElse(null);
            if (creator != null) {
                recipients.add(creator);
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        String title = "Skill \"" + skill.getName() + "\" has a new version.";
        String skillUrl = skillDetailUrl(skill.getId());
        String subscriberBody = "The skill \"" + skill.getName() + "\" you are subscribed to has a new version."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        String creatorBody = "Your skill \"" + skill.getName() + "\" has a new version."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        for (User sub : recipients) {
            UserSettings settings = userSettingsRepository.findById(sub.getId()).orElse(null);
            boolean isCreator = sub.getId().equals(skill.getCreatedBy());
            eventNotificationService.notify(actor, sub, settings,
                    new EventNotificationService.EventDetails(
                            title,
                            new EventNotificationService.EventContext(
                                    String.valueOf(skill.getId()), null, null, null, null),
                            "[AX Portal] " + title,
                            isCreator ? creatorBody : subscriberBody));
        }
    }

    /** Frontend URL that opens the skill detail popup (?detail={id}). */
    private String skillDetailUrl(Long skillId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/skills?detail=" + skillId;
    }
}
