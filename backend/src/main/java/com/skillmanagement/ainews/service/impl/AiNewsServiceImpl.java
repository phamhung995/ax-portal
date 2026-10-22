package com.skillmanagement.ainews.service.impl;

import com.skillmanagement.ainews.NewsNotFoundException;
import com.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.skillmanagement.ainews.dto.AiNewsDetail;
import com.skillmanagement.ainews.dto.AiNewsItem;
import com.skillmanagement.ainews.model.AiNews;
import com.skillmanagement.ainews.model.AiNewsComment;
import com.skillmanagement.ainews.model.AiNewsCommentVote;
import com.skillmanagement.ainews.model.AiNewsLike;
import com.skillmanagement.ainews.repository.AiNewsCommentRepository;
import com.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.skillmanagement.ainews.repository.AiNewsRepository;
import com.skillmanagement.ainews.service.AiNewsService;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI News screen + Home card data (AI_News_Screen_SRS.md §5).
 */
@Service
@RequiredArgsConstructor
public class AiNewsServiceImpl implements AiNewsService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final AiNewsRepository aiNewsRepository;
    private final AiNewsCommentRepository commentRepository;
    private final AiNewsCommentVoteRepository voteRepository;
    private final AiNewsLikeRepository likeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiNewsItem> list(int limit) {
        return aiNewsRepository.findActiveList(limit).stream()
                .map(row -> new AiNewsItem(
                        row.newsId(), row.title(), row.sender(), row.source(), row.createdAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiNewsDetail detail(Long newsId, User viewer) {
        AiNews news = requireActiveNews(newsId);
        AiNewsLike.Id likeId = new AiNewsLike.Id(newsId, viewer.getId());
        boolean liked = likeRepository.existsById(likeId);
        long likeCount = likeRepository.countByNewsIdNative(newsId);
        return new AiNewsDetail(
                news.getNewsId(), news.getTitle(), news.getSender(), news.getSource(),
                news.getBody(), news.getCreatedAt(), (int) likeCount, liked);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiNewsCommentItem> comments(Long newsId, User viewer) {
        requireActiveNews(newsId);
        // All comments (including deleted top-levels — they render as
        // tombstones "[deleted]" when they still have surviving replies, FR-19).
        List<AiNewsComment> all = commentRepository.findByNewsId(newsId);
        if (all.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = all.stream().map(AiNewsComment::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        Set<Long> votedIds = voteRepository
                .findAllById(all.stream()
                        .map(c -> new AiNewsCommentVote.Id(c.getCommentId(), viewer.getId()))
                        .toList())
                .stream().map(AiNewsCommentVote::getCommentId).collect(Collectors.toSet());

        // Visible = not deleted, OR deleted top-level that still has at least
        // one non-deleted reply (tombstone keeps the reply context, FR-19).
        Map<Long, Long> liveReplyCount = all.stream()
                .filter(c -> c.getDeletedAt() == null && c.getParentId() != null)
                .collect(Collectors.groupingBy(AiNewsComment::getParentId, Collectors.counting()));

        return all.stream()
                .filter(c -> c.getDeletedAt() == null
                        || (c.getParentId() == null
                            && liveReplyCount.getOrDefault(c.getCommentId(), 0L) > 0))
                .sorted(Comparator
                        // top-level first, replies after
                        .comparing((AiNewsComment c) -> c.getParentId() == null ? 0 : 1)
                        // top-level: newest first (FR-16); replies: oldest first (FR-17)
                        .thenComparing((AiNewsComment a, AiNewsComment b) -> {
                            int cmp = a.getCreatedAt().compareTo(b.getCreatedAt());
                            // top-level: newest first; replies: oldest first
                            return a.getParentId() == null ? -cmp : cmp;
                        })
                        .thenComparing(AiNewsComment::getCommentId))
                .map(c -> toItem(c, names, viewer.getId(), votedIds))
                .toList();
    }

    @Override
    @Transactional
    public AiNewsCommentItem addComment(Long newsId, User viewer, AiNewsCommentRequest request) {
        requireActiveNews(newsId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.COMMENT_EMPTY, "Comment content is empty.");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.COMMENT_TOO_LONG,
                    "Comment exceeds 1000 characters.");
        }

        Long effectiveParentId = request.parentId();
        if (effectiveParentId != null) {
            AiNewsComment parent = commentRepository.findById(effectiveParentId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_PARENT_NOT_FOUND,
                            "Parent comment not found."));
            if (!newsId.equals(parent.getNewsId())) {
                throw new BusinessException(BusinessErrorCode.COMMENT_PARENT_NOT_FOUND,
                        "Parent comment not found.");
            }
            if (parent.getDeletedAt() != null) {
                throw new BusinessException(BusinessErrorCode.COMMENT_PARENT_DELETED,
                        "Parent comment was deleted.");
            }
            // Depth > 2 → flatten to the thread root (FR-17).
            if (parent.getParentId() != null) {
                effectiveParentId = parent.getParentId();
            }
        }

        AiNewsComment comment = AiNewsComment.builder()
                .newsId(newsId)
                .userId(viewer.getId())
                .parentId(effectiveParentId)
                .content(content)
                .build();
        commentRepository.save(comment);

        if (effectiveParentId != null) {
            commentRepository.incrementReplyCount(effectiveParentId);
        }

        Map<Long, String> names = Map.of(viewer.getId(), viewer.getName());
        return toItem(comment, names, viewer.getId(), Set.of());
    }

    @Override
    @Transactional
    public void deleteComment(Long newsId, Long commentId, User viewer) {
        requireActiveNews(newsId);
        AiNewsComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND,
                        "Comment not found."));
        if (!newsId.equals(comment.getNewsId())) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND, "Comment not found.");
        }
        boolean own = comment.getUserId().equals(viewer.getId());
        if (!own && !isAdmin(viewer)) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_OWN,
                    "You can only delete your own comments.");
        }
        comment.setDeletedAt(java.time.Instant.now());
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public int toggleHelpful(Long newsId, Long commentId, User viewer) {
        requireActiveNews(newsId);
        AiNewsComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND,
                        "Comment not found."));
        if (!newsId.equals(comment.getNewsId()) || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND, "Comment not found.");
        }
        if (comment.getUserId().equals(viewer.getId())) {
            throw new BusinessException(BusinessErrorCode.CANNOT_VOTE_OWN_COMMENT,
                    "You cannot vote on your own comment.");
        }
        AiNewsCommentVote.Id voteId = new AiNewsCommentVote.Id(commentId, viewer.getId());
        if (voteRepository.existsById(voteId)) {
            voteRepository.deleteVote(commentId, viewer.getId());
            commentRepository.incrementHelpfulCount(commentId, -1);
        } else {
            // Race-safe: concurrent inserts cannot both succeed (composite PK);
            // xmax > 0 means a concurrent request inserted first → treat as
            // "already voted" and remove the duplicate instead of 500-ing.
            Long xmax = voteRepository.upsertVote(commentId, viewer.getId());
            if (xmax != null && xmax > 0) {
                voteRepository.deleteVote(commentId, viewer.getId());
                commentRepository.incrementHelpfulCount(commentId, -1);
            } else {
                commentRepository.incrementHelpfulCount(commentId, +1);
            }
        }
        return commentRepository.findById(commentId).map(AiNewsComment::getHelpfulCount).orElse(0);
    }

    @Override
    @Transactional
    public int toggleLike(Long newsId, User viewer) {
        requireActiveNews(newsId);
        AiNewsLike.Id likeId = new AiNewsLike.Id(newsId, viewer.getId());
        if (likeRepository.existsById(likeId)) {
            likeRepository.deleteLike(newsId, viewer.getId());
        } else {
            // Race-safe: concurrent inserts cannot both succeed (composite PK);
            // xmax > 0 means a concurrent request inserted first → treat as
            // "already liked" and remove the duplicate instead of 500-ing.
            Long xmax = likeRepository.upsertLike(newsId, viewer.getId());
            if (xmax != null && xmax > 0) {
                likeRepository.deleteLike(newsId, viewer.getId());
            }
        }
        return (int) likeRepository.countByNewsIdNative(newsId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private AiNews requireActiveNews(Long newsId) {
        AiNews news = aiNewsRepository.findByNewsIdAndArchiveYn(newsId, "N");
        if (news == null) {
            // 404 NEWS_NOT_FOUND for both "not found" and "archived" — the reason is
            // not distinguished for the client (AI_News_Screen_SRS.md §5.4).
            throw new NewsNotFoundException("News not found or archived.");
        }
        return news;
    }

    private boolean isAdmin(User viewer) {
        return viewer.getRole() != null && "ADMIN_USER".equals(viewer.getRole().getName());
    }

    private AiNewsCommentItem toItem(AiNewsComment c, Map<Long, String> names,
                                     Long viewerId, Set<Long> votedIds) {
        return new AiNewsCommentItem(
                c.getCommentId(),
                c.getNewsId(),
                c.getParentId(),
                names.getOrDefault(c.getUserId(), "Deleted user"),
                c.getDeletedAt() != null ? null : c.getContent(),
                c.getHelpfulCount(),
                c.getReplyCount(),
                c.getCreatedAt(),
                c.getDeletedAt() != null,
                c.getUserId().equals(viewerId),
                votedIds.contains(c.getCommentId()));
    }
}
