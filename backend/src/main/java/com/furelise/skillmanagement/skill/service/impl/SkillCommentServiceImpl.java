package com.furelise.skillmanagement.skill.service.impl;

import com.furelise.skillmanagement.skill.dto.SkillCommentItem;
import com.furelise.skillmanagement.skill.dto.SkillCommentLikeResponse;
import com.furelise.skillmanagement.skill.dto.SkillCommentPage;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillComment;
import com.furelise.skillmanagement.skill.model.SkillCommentLike;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.skill.repository.SkillCommentLikeRepository;
import com.furelise.skillmanagement.skill.repository.SkillCommentRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillCommentService;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comment implementation (Detail SRS §5.6).
 * Comments are only allowed on PUBLISHED skills; deletion is soft.
 * 2026-11-08: threaded (depth 2 — a reply points at its top-level comment),
 * "Helpful" likes (one per user per comment) and self-edit.
 * Top-level threads are paged oldest-first (chronological, per user
 * decision); replies are nested under their thread, also oldest-first.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillCommentServiceImpl implements SkillCommentService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int COMMENT_MAX_PER_MINUTE = 30;
    private static final int COMMENT_WINDOW_SECONDS = 60;

    private final SkillCommentRepository skillCommentRepository;
    private final SkillCommentLikeRepository skillCommentLikeRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final SkillAccessHelper skillAccessHelper;
    private final RateLimiter rateLimiter;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public SkillCommentPage list(Long skillId, User viewer, int page, int size) {
        skillAccessHelper.getVisible(skillId, viewer);
        int p = page < 1 ? 1 : page;
        int sz = size < 1 ? 10 : size;
        // Chronological (oldest first) — user decision 2026-10: comments are
        // sorted by time, not by interaction score. id tie-break keeps
        // pagination stable.
        Pageable pageable = PageRequest.of(p - 1, sz,
                Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<SkillComment> threads = skillCommentRepository
                .findBySkillIdAndParentIdIsNullAndDeletedAtIsNull(skillId, pageable);

        // Replies of the paged threads (non-deleted, chronological).
        List<Long> threadIds = threads.getContent().stream().map(SkillComment::getId).toList();
        Map<Long, List<SkillComment>> repliesByThread = threadIds.isEmpty() ? Map.of()
                : skillCommentRepository.findByParentIdInAndDeletedAtIsNull(threadIds,
                        Sort.by(Sort.Direction.ASC, "createdAt"))
                    .stream().collect(Collectors.groupingBy(SkillComment::getParentId));

        // Users of threads + replies (batched — avoids N+1).
        Set<Long> userIds = new HashSet<>();
        threads.getContent().forEach(c -> userIds.add(c.getUserId()));
        repliesByThread.values().forEach(list -> list.forEach(c -> userIds.add(c.getUserId())));
        Map<Long, User> users = loadUsers(userIds);

        // Viewer's likes on the visible comments (batched).
        Set<Long> allCommentIds = new HashSet<>(threadIds);
        repliesByThread.values().forEach(list -> list.forEach(c -> allCommentIds.add(c.getId())));
        Set<Long> likedIds = allCommentIds.isEmpty() ? Set.of()
                : skillCommentLikeRepository
                    .findByUserIdInAndCommentIdIn(List.of(viewer.getId()), allCommentIds)
                    .stream().map(SkillCommentLike::getCommentId).collect(Collectors.toSet());

        boolean isAdmin = isAdmin(viewer);
        List<SkillCommentItem> items = threads.getContent().stream()
                .map(c -> toItem(c, users, viewer, isAdmin, likedIds,
                        repliesByThread.getOrDefault(c.getId(), List.of())))
                .toList();
        return new SkillCommentPage(threads.getTotalElements(), p, sz, items);
    }

    @Override
    @Transactional
    public void add(Long skillId, User user, String content, Long parentId) {
        if (!rateLimiter.allow("skill-comment:" + user.getId(),
                COMMENT_MAX_PER_MINUTE, COMMENT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())
                && !skill.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_APPROVED,
                    "Only published skills can be commented.");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment is at most 1000 characters.");
        }

        Long resolvedParent = null;
        if (parentId != null) {
            SkillComment parent = skillCommentRepository.findById(parentId).orElse(null);
            if (parent == null || !parent.getSkillId().equals(skillId)
                    || parent.getDeletedAt() != null) {
                throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_NOT_FOUND,
                        "Parent comment not found.");
            }
            // Depth 2 only: replying to a reply targets its top-level thread.
            resolvedParent = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        }

        skillCommentRepository.save(SkillComment.builder()
                .skillId(skillId)
                .userId(user.getId())
                .parentId(resolvedParent)
                .content(trimmed)
                .build());

        // Notify the thread starter (not the commenter, not on self-reply).
        if (resolvedParent == null) {
            notifyCreator(skill, user);
        }
    }

    @Override
    @Transactional
    public void delete(Long skillId, Long commentId, User user) {
        skillAccessHelper.getVisible(skillId, user);
        SkillComment comment = skillCommentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getSkillId().equals(skillId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_NOT_FOUND, "Comment not found.");
        }
        boolean isAdmin = isAdmin(user);
        if (!isAdmin && !comment.getUserId().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_DELETE_FORBIDDEN,
                    "You can only delete your own comments.");
        }
        comment.setDeletedAt(Instant.now());
        skillCommentRepository.save(comment);
    }

    @Override
    @Transactional
    public SkillCommentLikeResponse toggleLike(Long skillId, Long commentId, User user) {
        skillAccessHelper.getVisible(skillId, user);
        SkillComment comment = skillCommentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getSkillId().equals(skillId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_NOT_FOUND, "Comment not found.");
        }
        SkillCommentLike like = skillCommentLikeRepository
                .findById(new SkillCommentLike.Id(commentId, user.getId())).orElse(null);
        if (like != null) {
            skillCommentLikeRepository.delete(like);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        } else {
            skillCommentLikeRepository.save(SkillCommentLike.builder()
                    .commentId(commentId).userId(user.getId()).build());
            comment.setLikeCount(comment.getLikeCount() + 1);
        }
        skillCommentRepository.save(comment);
        return new SkillCommentLikeResponse(like == null, comment.getLikeCount());
    }

    @Override
    @Transactional
    public void edit(Long skillId, Long commentId, User user, String content) {
        skillAccessHelper.getVisible(skillId, user);
        SkillComment comment = skillCommentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getSkillId().equals(skillId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_NOT_FOUND, "Comment not found.");
        }
        if (!comment.getUserId().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_DELETE_FORBIDDEN,
                    "You can only edit your own comments.");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment is at most 1000 characters.");
        }
        comment.setContent(trimmed);
        comment.setUpdatedAt(Instant.now());
        skillCommentRepository.save(comment);
    }

    /** Maps a thread + its replies to the nested DTO. */
    private SkillCommentItem toItem(SkillComment c, Map<Long, User> users, User viewer,
                                    boolean isAdmin, Set<Long> likedIds,
                                    List<SkillComment> replies) {
        List<SkillCommentItem> replyItems = replies.stream()
                .sorted(Comparator.comparing(SkillComment::getCreatedAt)
                        .thenComparing(SkillComment::getId))
                .map(r -> toItem(r, users, viewer, isAdmin, likedIds, List.of()))
                .toList();
        User author = users.get(c.getUserId());
        return new SkillCommentItem(
                c.getId(), c.getUserId(),
                author != null ? author.getName() : null,
                author != null ? author.getAvatarUrl() : null,
                c.getParentId(), c.getContent(),
                c.getLikeCount() == null ? 0 : c.getLikeCount(),
                replyItems.size(),
                c.getCreatedAt(), c.getUpdatedAt(),
                c.getUserId().equals(viewer.getId()),
                likedIds.contains(c.getId()),
                isAdmin || c.getUserId().equals(viewer.getId()),
                c.getUserId().equals(viewer.getId()),
                replyItems);
    }

    /** In-app + email notification to the skill creator (not the commenter). */
    private void notifyCreator(Skill skill, User commenter) {
        Long creatorId = skill.getCreatedBy();
        if (creatorId == null || creatorId.equals(commenter.getId())) {
            return;
        }
        User creator = userRepository.findById(creatorId).orElse(null);
        if (creator == null) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(creatorId).orElse(null);
        String title = "New comment on skill \"" + skill.getName() + "\"";
        String skillUrl = skillDetailUrl(skill.getId());
        String body = "\"" + commenter.getName() + "\" commented on your skill \""
                + skill.getName() + "\"."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        eventNotificationService.notify(commenter, creator, settings,
                new EventNotificationService.EventDetails(
                        title,
                        new EventNotificationService.EventContext(
                                String.valueOf(skill.getId()), null, null, null, null),
                        "[AX Portal] " + title, body));
    }

    /** Frontend URL that opens the skill detail popup (?detail={id}). */
    private String skillDetailUrl(Long skillId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/skills?detail=" + skillId;
    }

    private Map<Long, User> loadUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }
}
