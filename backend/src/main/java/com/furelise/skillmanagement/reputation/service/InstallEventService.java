package com.furelise.skillmanagement.reputation.service;

import com.furelise.skillmanagement.exception.ResourceNotFoundException;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.reputation.dto.InstallEventRequest;
import com.furelise.skillmanagement.reputation.dto.InstallEventResponse;
import com.furelise.skillmanagement.reputation.model.InstallEvent;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.repository.InstallEventRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Records install events from the companion app (GĐ6, BA FR-6) and refreshes
 * the item's reputation score. One row per (target, user, version): an exact
 * duplicate (companion retry) is acknowledged but never counted twice.
 * Unknown users do not fail the report — the event is stored anonymously
 * (user_id NULL) so a knoxId typo cannot lose install data.
 */
@Service
@RequiredArgsConstructor
public class InstallEventService {

    private final InstallEventRepository eventRepository;
    private final ReputationService reputationService;
    private final SkillRepository skillRepository;
    private final McpMasterRepository mcpMasterRepository;
    private final UserRepository userRepository;

    @Transactional
    public InstallEventResponse record(InstallEventRequest request) {
        String type = request.targetType() == null
                ? null : request.targetType().toUpperCase(Locale.ROOT);
        if (!"SKILL".equals(type) && !"MCP".equals(type)) {
            throw new IllegalArgumentException("Unknown target type: " + request.targetType());
        }
        if (request.targetId() == null || request.success() == null) {
            throw new IllegalArgumentException("targetId and success are required.");
        }
        boolean exists = "SKILL".equals(type)
                ? skillRepository.findById(request.targetId())
                        .map(s -> !"Y".equals(s.getDeletedYn())).orElse(false)
                : mcpMasterRepository.findById(request.targetId())
                        .map(m -> !"Y".equals(m.getDeletedYn())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException(
                    "Target not found: " + type + "/" + request.targetId());
        }

        Long userId = resolveUserId(request);
        String version = request.version() == null ? null : request.version().trim();
        if (version != null && version.isEmpty()) {
            version = null;
        }

        if (eventRepository.existsDuplicate(type, request.targetId(), userId, version)) {
            // Duplicate delivery (companion retry): acknowledge, do not
            // double-count, do not refresh — the score already includes it.
            // getReputation never returns null (empty row when never
            // computed), so the cached row is read directly.
            var cached = reputationService.getReputation(type, request.targetId());
            return new InstallEventResponse(type, request.targetId(), true,
                    cached.score(), cached.tier());
        }

        InstallEvent event = InstallEvent.builder()
                .targetType(type)
                .targetId(request.targetId())
                .userId(userId)
                .success(request.success())
                .version(version)
                .build();
        eventRepository.save(event);

        ReputationScore refreshed = "SKILL".equals(type)
                ? reputationService.refreshSkill(request.targetId())
                : reputationService.refreshMcp(request.targetId());
        return new InstallEventResponse(type, request.targetId(), false,
                refreshed == null ? null : refreshed.getScore(),
                refreshed == null ? "NEW" : refreshed.getTier());
    }

    /**
     * userId wins when given (and must exist); otherwise knoxId is resolved
     * to the first matching user; otherwise the install is anonymous.
     */
    private Long resolveUserId(InstallEventRequest request) {
        if (request.userId() != null) {
            if (!userRepository.existsById(request.userId())) {
                throw new IllegalArgumentException("Unknown user: " + request.userId());
            }
            return request.userId();
        }
        if (request.knoxId() != null && !request.knoxId().isBlank()) {
            return userRepository.findByKnoxIdIgnoreCase(request.knoxId().trim())
                    .stream().findFirst().map(u -> u.getId()).orElse(null);
        }
        return null;
    }
}
