package com.skillmanagement.skill.service;

import com.skillmanagement.mail.EmailService;
import com.skillmanagement.notification.model.Notification;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.skill.dto.SkillPendingMetadata;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillCategoryMapping;
import com.skillmanagement.skill.model.SkillFile;
import com.skillmanagement.skill.model.SkillStageMapping;
import com.skillmanagement.skill.model.SkillSubscription;
import com.skillmanagement.skill.model.SkillVersion;
import com.skillmanagement.skill.model.SkillVersionFile;
import com.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.skillmanagement.skill.repository.SkillFileRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.skillmanagement.skill.repository.SkillVersionFileRepository;
import com.skillmanagement.skill.repository.SkillVersionRepository;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserSettings;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    /**
     * Commits a pending version update: snapshot the superseded state,
     * apply the staged metadata/files, bump the version.
     * The skill's status is already set by the caller (workflow).
     */
    @Transactional
    public void completeVersionUpdate(Skill skill) {
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

        notifySubscribers(skill);
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

    /** In-app + email notification to all subscribers of the new version. */
    private void notifySubscribers(Skill skill) {
        List<SkillSubscription> subs = subscriptionRepository.findBySkillId(skill.getId());
        if (subs.isEmpty()) {
            return;
        }
        List<User> subscribers = userRepository.findAllById(
                subs.stream().map(SkillSubscription::getUserId).toList());
        String title = "Skill \"" + skill.getName() + "\" has a new version.";
        String body = "The skill \"" + skill.getName() + "\" you are subscribed to has a new version.";
        for (User sub : subscribers) {
            UserSettings settings = userSettingsRepository.findById(sub.getId())
                    .orElse(UserSettings.builder().userId(sub.getId())
                            .inappNotify(true).emailNotify(true).build());
            if (settings.isInappNotify()) {
                notificationRepository.save(Notification.builder()
                        .userId(sub.getId())
                        .title(title)
                        .skillId(String.valueOf(skill.getId()))
                        .read(false)
                        .build());
            }
            if (settings.isEmailNotify()) {
                try {
                    emailService.sendNotificationEmail(sub.getEmail(), title, body);
                } catch (Exception e) {
                    log.warn("Không thể gửi email thông báo version mới cho {}: {}",
                            sub.getEmail(), e.getMessage());
                }
            }
        }
    }
}
