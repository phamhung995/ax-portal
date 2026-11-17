package com.furelise.skillmanagement.mcp.service.impl;

import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpRatingResponse;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpRating;
import com.furelise.skillmanagement.mcp.repository.McpRatingRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpRatingService;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Star rating implementation (MCP Detail).
 * One rating per user per MCP; re-rating is an UPSERT.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class McpRatingServiceImpl implements McpRatingService {

    private static final int RATING_MAX_PER_MINUTE = 10;
    private static final int RATING_WINDOW_SECONDS = 60;

    private final McpRatingRepository mcpRatingRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final McpAccessHelper mcpAccessHelper;
    private final RateLimiter rateLimiter;
    private final ReputationService reputationService;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public McpRatingResponse getRating(Long mcpId) {
        BigDecimal avg = mcpRatingRepository.averageRatingByMcpId(mcpId);
        long count = mcpRatingRepository.countByMcpId(mcpId);
        return new McpRatingResponse(
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP).doubleValue() : 0.0, count);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getMyRating(Long mcpId, Long userId) {
        return mcpRatingRepository.findByMcpIdAndUserId(mcpId, userId)
                .map(r -> r.getRating().setScale(1, RoundingMode.HALF_UP).doubleValue())
                .orElse(null);
    }

    @Override
    @Transactional
    public McpRatingResponse upsertRating(Long mcpId, User user, double rating) {
        if (!rateLimiter.allow("mcp-rating:" + user.getId(),
                RATING_MAX_PER_MINUTE, RATING_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.MCP_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        if (rating < 1.0 || rating > 5.0 || Math.round(rating * 2) / 2.0 != rating) {
            throw new BusinessException(BusinessErrorCode.MCP_INVALID_RATING,
                    "Rating must be between 1 and 5 in steps of 0.5.");
        }
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!McpMaster.STATUS_APPROVED.equals(mcp.getStatus())
                && !mcp.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_APPROVED,
                    "Only published MCPs can be rated.");
        }

        McpRating existing = mcpRatingRepository
                .findByMcpIdAndUserId(mcpId, user.getId())
                .orElseGet(() -> McpRating.builder()
                        .mcpId(mcpId)
                        .userId(user.getId())
                        .build());
        existing.setRating(BigDecimal.valueOf(rating).setScale(1, RoundingMode.HALF_UP));
        mcpRatingRepository.save(existing);

        // Reputation: a new rating moves the MCP's cached score.
        // (Q2: the creator's own rating is stored but excluded from the score.)
        reputationService.refreshMcp(mcpId);

        notifyCreator(mcp, user, existing.getRating());
        return getRating(mcpId);
    }

    /** In-app + email notification to the MCP creator (not the rater). */
    private void notifyCreator(McpMaster mcp, User rater, BigDecimal rating) {
        Long creatorId = mcp.getCreatedBy();
        if (creatorId == null || creatorId.equals(rater.getId())) {
            return;
        }
        User creator = userRepository.findById(creatorId).orElse(null);
        if (creator == null) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(creatorId).orElse(null);
        String title = "New rating on MCP \"" + mcp.getMcpName() + "\"";
        String mcpUrl = mcpDetailUrl(mcp.getId());
        String body = "\"" + rater.getName() + "\" rated your MCP \""
                + mcp.getMcpName() + "\" " + rating.toPlainString() + "/5."
                + (mcpUrl != null ? "\n\n" + mcpUrl : "");
        eventNotificationService.notify(rater, creator, settings,
                new EventNotificationService.EventDetails(
                        title,
                        new EventNotificationService.EventContext(
                                String.valueOf(mcp.getId()), null, null, null, null),
                        "[AX Portal] " + title, body));
    }

    /** Frontend URL that opens the MCP detail popup (?detail={id}). */
    private String mcpDetailUrl(Long mcpId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/mcps?detail=" + mcpId;
    }
}
