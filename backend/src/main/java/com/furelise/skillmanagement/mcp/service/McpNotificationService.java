package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.model.McpSubscription;
import com.furelise.skillmanagement.mcp.repository.McpSubscriptionRepository;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService.Blocker;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * In-app notification for MCP-related events (Skill ↔ MCP integration +
 * MCP workflow).
 *
 * <p>Retry 3 times exponential backoff (1s, 4s, 16s); after 3 failures the
 * notification is dropped with an error log. Does NOT block the main
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpNotificationService {

    private final NotificationRepository notificationRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RoleRepository roleRepository;
    private final McpSubscriptionRepository mcpSubscriptionRepository;

    private static final long[] BACKOFF_MS = {1000L, 4000L, 16000L};

    /**
     * Notify the owner of a skill that is held in DRAFT because its MCP
     * dependencies are not ready (validation failed at submit).
     *
     * @param skillId  the skill that was blocked
     * @param blockers the MCP blockers from validation
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifySkillBlocked(Long skillId, List<Blocker> blockers) {
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null) {
            log.warn("Cannot notify: skill {} not found", skillId);
            return;
        }
        Long ownerId = skill.getCreatedBy();
        StringBuilder msg = new StringBuilder("Skill '");
        msg.append(skill.getName()).append("' is held in DRAFT. Waiting for MCP: ");
        for (int i = 0; i < blockers.size(); i++) {
            Blocker b = blockers.get(i);
            if (i > 0) msg.append(", ");
            msg.append(b.getMcpCode());
            switch (b.getReason()) {
                case "NOT_APPROVED" -> msg.append(" (not approved)");
                case "MCP_DELETED" -> msg.append(" (deleted)");
            }
        }
        sendWithRetry(ownerId, msg.toString(), null, "SKILL_BLOCKED");
    }

    /**
     * Notify ALL admins that an MCP was submitted for approval.
     *
     * @param mcpId         the submitted MCP
     * @param mcpName       MCP display name (for the title)
     * @param submitterName owner name (for the title)
     */
    @Transactional
    public void notifySubmitForApproval(Long mcpId, String mcpName, String submitterName) {
        String msg = "MCP '" + mcpName + "' by " + submitterName
                + " has been submitted for approval.";
        for (User admin : loadAdmins()) {
            UserSettings settings = userSettingsRepository.findById(admin.getId())
                    .orElse(UserSettings.builder().userId(admin.getId())
                            .inappNotify(true).emailNotify(true).build());
            if (!settings.isInappNotify()) {
                continue;
            }
            sendWithRetry(admin.getId(), msg, mcpId, "MCP_SUBMIT");
        }
    }

    /**
     * Notify the MCP owner when an admin decides (approve / reject).
     *
     * @param mcpId           the decided MCP
     * @param mcpName         MCP display name (for the title)
     * @param ownerId         the owner (creator) of the MCP
     * @param approved        true = approved, false = rejected
     * @param rejectionReason required when rejected
     */
    @Transactional
    public void notifyOwnerDecision(Long mcpId, String mcpName, Long ownerId,
                                    boolean approved, String rejectionReason) {
        UserSettings settings = userSettingsRepository.findById(ownerId)
                .orElse(UserSettings.builder().userId(ownerId)
                        .inappNotify(true).emailNotify(true).build());
        if (!settings.isInappNotify()) {
            return;
        }
        String msg = approved
                ? "Your MCP '" + mcpName + "' has been approved."
                : "Your MCP '" + mcpName + "' was rejected."
                        + (rejectionReason != null && !rejectionReason.isBlank()
                            ? " Reason: " + rejectionReason : "");
        sendWithRetry(ownerId, msg, mcpId,
                approved ? "MCP_APPROVED" : "MCP_REJECTED");
    }

    /**
     * Notify ALL subscribers of an MCP when a new version is approved.
     * In-app only, gated by UserSettings.inappNotify, retry 3x.
     *
     * @param mcpId   the approved MCP
     * @param mcpName MCP display name (for the title)
     */
    @Transactional
    public void notifySubscribers(Long mcpId, String mcpName) {
        List<McpSubscription> subs = mcpSubscriptionRepository.findByMcpId(mcpId);
        if (subs.isEmpty()) {
            return;
        }
        String msg = "MCP '" + mcpName + "' has a new approved version.";
        for (McpSubscription sub : subs) {
            UserSettings settings = userSettingsRepository.findById(sub.getUserId())
                    .orElse(UserSettings.builder().userId(sub.getUserId())
                            .inappNotify(true).emailNotify(true).build());
            if (!settings.isInappNotify()) {
                continue;
            }
            sendWithRetry(sub.getUserId(), msg, mcpId, "MCP_NEW_VERSION");
        }
    }

    /** Loads all users with the ADMIN_USER role. */
    private List<User> loadAdmins() {
        var adminRole = roleRepository.findByName("ADMIN_USER").orElse(null);
        if (adminRole == null) {
            return List.of();
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && adminRole.getId().equals(u.getRole().getId()))
                .toList();
    }

    /**
     * Sends an in-app notification with 3 retries (exponential backoff).
     * On final failure → error log (notification dropped).
     */
    private void sendWithRetry(Long recipientId, String message, Long mcpId, String type) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                }
                notificationRepository.save(Notification.builder()
                        .userId(recipientId)
                        .title(message)
                        .mcpId(mcpId)
                        .build());
                return; // success
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Notification attempt {} failed for user {}: {}",
                        attempt + 1, recipientId, e.getMessage());
            }
        }
        // All 3 attempts failed → drop with error log
        log.error("Notification to user {} failed after 3 retries, dropping (type={})", recipientId, type);
    }
}
