package com.furelise.skillmanagement.mcp.service.impl;

import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpCommentItem;
import com.furelise.skillmanagement.mcp.dto.McpCommentPage;
import com.furelise.skillmanagement.mcp.model.McpComment;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpCommentRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpCommentService;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comment implementation (MCP Detail).
 * Comments are only allowed on PUBLISHED MCPs; deletion is soft.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class McpCommentServiceImpl implements McpCommentService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int COMMENT_MAX_PER_MINUTE = 30;
    private static final int COMMENT_WINDOW_SECONDS = 60;

    private final McpCommentRepository mcpCommentRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final McpAccessHelper mcpAccessHelper;
    private final RateLimiter rateLimiter;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public McpCommentPage list(Long mcpId, User viewer, int page, int size) {
        mcpAccessHelper.getVisible(mcpId, viewer);
        int p = page < 1 ? 1 : page;
        int sz = size < 1 ? 10 : size;
        Pageable pageable = PageRequest.of(p - 1, sz,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<McpComment> result = mcpCommentRepository.findByMcpIdAndDeletedAtIsNull(mcpId, pageable);

        Map<Long, User> users = loadUsers(result.getContent());
        boolean isAdmin = isAdmin(viewer);
        List<McpCommentItem> items = result.getContent().stream().map(c -> new McpCommentItem(
                c.getId(), c.getUserId(),
                users.get(c.getUserId()) != null ? users.get(c.getUserId()).getName() : null,
                c.getContent(), c.getCreatedAt(),
                isAdmin || c.getUserId().equals(viewer.getId())))
                .toList();
        return new McpCommentPage(result.getTotalElements(), p, sz, items);
    }

    @Override
    @Transactional
    public void add(Long mcpId, User user, String content) {
        if (!rateLimiter.allow("mcp-comment:" + user.getId(),
                COMMENT_MAX_PER_MINUTE, COMMENT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.MCP_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!McpMaster.STATUS_APPROVED.equals(mcp.getStatus())
                && !mcp.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_APPROVED,
                    "Only published MCPs can be commented.");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(BusinessErrorCode.MCP_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_COMMENT_TOO_LONG,
                    "Comment is at most 1000 characters.");
        }
        mcpCommentRepository.save(McpComment.builder()
                .mcpId(mcpId)
                .userId(user.getId())
                .content(content)
                .build());
        notifyCreator(mcp, user);
    }

    @Override
    @Transactional
    public void delete(Long mcpId, Long commentId, User user) {
        mcpAccessHelper.getVisible(mcpId, user);
        McpComment comment = mcpCommentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getMcpId().equals(mcpId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.MCP_COMMENT_NOT_FOUND, "Comment not found.");
        }
        boolean isAdmin = isAdmin(user);
        if (!isAdmin && !comment.getUserId().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.MCP_DELETE_FORBIDDEN,
                    "You can only delete your own comments.");
        }
        comment.setDeletedAt(Instant.now());
        mcpCommentRepository.save(comment);
    }

    /** In-app + email notification to the MCP creator (not the commenter). */
    private void notifyCreator(McpMaster mcp, User commenter) {
        Long creatorId = mcp.getCreatedBy();
        if (creatorId == null || creatorId.equals(commenter.getId())) {
            return;
        }
        User creator = userRepository.findById(creatorId).orElse(null);
        if (creator == null) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(creatorId).orElse(null);
        String title = "New comment on MCP \"" + mcp.getMcpName() + "\"";
        String mcpUrl = mcpDetailUrl(mcp.getId());
        String body = "\"" + commenter.getName() + "\" commented on your MCP \""
                + mcp.getMcpName() + "\"."
                + (mcpUrl != null ? "\n\n" + mcpUrl : "");
        eventNotificationService.notify(commenter, creator, settings,
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

    private Map<Long, User> loadUsers(List<McpComment> comments) {
        Set<Long> ids = comments.stream().map(McpComment::getUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }
}
