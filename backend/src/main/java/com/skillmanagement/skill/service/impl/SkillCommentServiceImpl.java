package com.skillmanagement.skill.service.impl;

import com.skillmanagement.skill.dto.SkillCommentItem;
import com.skillmanagement.skill.dto.SkillCommentPage;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillComment;
import com.skillmanagement.user.model.User;
import com.skillmanagement.skill.repository.SkillCommentRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.common.RateLimiter;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.SkillCommentService;
import lombok.RequiredArgsConstructor;
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
 * Comment implementation (Detail SRS §5.6).
 * Comments are only allowed on PUBLISHED skills; deletion is soft.
 */
@Service
@RequiredArgsConstructor
public class SkillCommentServiceImpl implements SkillCommentService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int COMMENT_MAX_PER_MINUTE = 30;
    private static final int COMMENT_WINDOW_SECONDS = 60;

    private final SkillCommentRepository skillCommentRepository;
    private final UserRepository userRepository;
    private final SkillAccessHelper skillAccessHelper;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional(readOnly = true)
    public SkillCommentPage list(Long skillId, User viewer, int page, int size) {
        skillAccessHelper.getVisible(skillId, viewer);
        int p = page < 1 ? 1 : page;
        int sz = size < 1 ? 10 : size;
        Pageable pageable = PageRequest.of(p - 1, sz, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SkillComment> result = skillCommentRepository.findBySkillIdAndDeletedAtIsNull(skillId, pageable);

        Map<Long, User> users = loadUsers(result.getContent());
        boolean isAdmin = isAdmin(viewer);
        List<SkillCommentItem> items = result.getContent().stream().map(c -> new SkillCommentItem(
                c.getId(), c.getUserId(),
                users.get(c.getUserId()) != null ? users.get(c.getUserId()).getName() : null,
                c.getContent(), c.getCreatedAt(),
                isAdmin || c.getUserId().equals(viewer.getId())))
                .toList();
        return new SkillCommentPage(result.getTotalElements(), p, sz, items);
    }

    @Override
    @Transactional
    public void add(Long skillId, User user, String content) {
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
        if (content == null || content.isBlank()) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment is at most 1000 characters.");
        }
        skillCommentRepository.save(SkillComment.builder()
                .skillId(skillId)
                .userId(user.getId())
                .content(content)
                .build());
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

    private Map<Long, User> loadUsers(List<SkillComment> comments) {
        Set<Long> ids = comments.stream().map(SkillComment::getUserId).collect(Collectors.toSet());
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
