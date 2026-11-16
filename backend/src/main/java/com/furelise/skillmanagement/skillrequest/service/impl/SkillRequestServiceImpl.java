package com.furelise.skillmanagement.skillrequest.service.impl;

import com.furelise.skillmanagement.ainews.email.CommentHtmlSanitizer;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skillrequest.dto.MentionUserItem;
import com.furelise.skillmanagement.skillrequest.dto.ResolvedSkillItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCommentItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCommentPage;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCreateRequest;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedResult;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFileItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestSuggestions;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestStatsResult;
import com.furelise.skillmanagement.skillrequest.dto.TopCommentItem;
import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestComment;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestCommentLike;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestFile;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestVote;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestCommentLikeRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestCommentRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestFileRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestVoteRepository;
import com.furelise.skillmanagement.skillrequest.service.SkillRequestFileStorageService;
import com.furelise.skillmanagement.skillrequest.service.SkillRequestService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core Skill Request Management (feed) implementation.
 * All requests are visible to every authenticated user (no scope concept).
 */
@Service
@Slf4j
public class SkillRequestServiceImpl implements SkillRequestService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_HASHTAGS = 10;
    private static final int MAX_HASHTAG_LENGTH = 30;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int SUGGESTION_LIMIT = 5;

    private static final int CREATE_MAX_PER_MINUTE = 5;
    private static final int CREATE_WINDOW_SECONDS = 60;
    private static final int VOTE_MAX_PER_MINUTE = 10;
    private static final int VOTE_WINDOW_SECONDS = 60;
    private static final int COMMENT_MAX_PER_MINUTE = 30;
    private static final int COMMENT_WINDOW_SECONDS = 60;
    private static final int MAX_COMMENT_DEPTH = 3;
    private static final int MAX_FILES = 10;
    /** Total upload budget per request: 10 files x 10MB. */
    private static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024;

    /** Plain-text limit for comment content (HTML tags don't count). */
    private static final int MAX_COMMENT_LENGTH = 1000;
    /**
     * Cap on the sanitized HTML length. The plain-text limit (1000) does not
     * bound the stored HTML — embedded base64 images or deeply nested markup
     * could make the row arbitrarily large (same policy as AI News comments).
     */
    private static final int MAX_COMMENT_HTML_LENGTH = 20_000;
    /**
     * Cap on distinct users notified per comment. The mention id comes from
     * client-supplied HTML (data-user-id), so without a cap a single comment
     * could carry hundreds of ids and spam in-app + email notifications.
     */
    private static final int MAX_MENTIONS_PER_COMMENT = 10;
    private static final int MENTION_SEARCH_LIMIT = 10;

    // Feed ranking: score = (voteCount*2 + commentCount) * timeDecay(age)
    private static final double VOTE_WEIGHT = 2.0;
    private static final double COMMENT_WEIGHT = 1.0;
    private static final Duration DECAY_TIER_1 = Duration.ofDays(2);   // decay 1.0
    private static final Duration DECAY_TIER_2 = Duration.ofDays(7);   // decay 0.5, older 0.1
    private static final double DECAY_RECENT = 1.0;
    private static final double DECAY_MID = 0.5;
    private static final double DECAY_OLD = 0.1;
    private static final int FEED_DEFAULT_LIMIT = 10;
    private static final int FEED_MAX_LIMIT = 50;
    private static final int TOP_COMMENT_PREVIEW = 2;
    private static final int RESOLVED_SKILL_PREVIEW = 3;
    private static final int STATS_TOP_LIMIT = 10;
    private static final int CONTRIBUTOR_TOP_LIMIT = 5;
    private static final Duration TOP_VOTED_WINDOW = Duration.ofDays(7);
    private static final Duration HASHTAG_WINDOW = Duration.ofDays(30);

    private final SkillRequestRepository skillRequestRepository;
    private final SkillRequestVoteRepository voteRepository;
    private final SkillRequestCommentRepository commentRepository;
    private final SkillRequestCommentLikeRepository likeRepository;
    private final SkillRequestFileRepository skillRequestFileRepository;
    private final SkillRequestFileStorageService skillRequestFileStorageService;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final RateLimiter rateLimiter;
    private final CommentHtmlSanitizer commentHtmlSanitizer;

    /**
     * Runs the post-commit @mention notification work in its OWN transaction.
     * The afterCommit callback fires while the comment transaction is still
     * bound to the thread (Spring runs afterCommit BEFORE cleanup), so a
     * default PROPAGATION_REQUIRED template would PARTICIPATE in the already
     * committed transaction and never commit its writes. REQUIRES_NEW
     * suspends that bound transaction and starts a fresh one (same pattern
     * as AiNewsCommentServiceImpl.notificationTransaction).
     */
    private final TransactionTemplate notificationTransaction;

    public SkillRequestServiceImpl(SkillRequestRepository skillRequestRepository,
                                   SkillRequestVoteRepository voteRepository,
                                   SkillRequestCommentRepository commentRepository,
                                   SkillRequestCommentLikeRepository likeRepository,
                                   SkillRequestFileRepository skillRequestFileRepository,
                                   SkillRequestFileStorageService skillRequestFileStorageService,
                                   SkillRepository skillRepository,
                                   UserRepository userRepository,
                                   UserSettingsRepository userSettingsRepository,
                                   EventNotificationService eventNotificationService,
                                   RateLimiter rateLimiter,
                                   CommentHtmlSanitizer commentHtmlSanitizer,
                                   PlatformTransactionManager transactionManager) {
        this.skillRequestRepository = skillRequestRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.skillRequestFileRepository = skillRequestFileRepository;
        this.skillRequestFileStorageService = skillRequestFileStorageService;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.eventNotificationService = eventNotificationService;
        this.rateLimiter = rateLimiter;
        this.commentHtmlSanitizer = commentHtmlSanitizer;
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Value("${app.upload.skill-request-url-prefix:/uploads/skill-requests}")
    private String skillRequestUrlPrefix;

    /** Public frontend base URL — appended to request links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    // ------------------------------------------------------------------
    // Feed (ranked, cursor-paged)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestFeedResult feed(String cursor, int limit, boolean mine, String title, User viewer) {
        int size = limit < 1 ? FEED_DEFAULT_LIMIT : Math.min(limit, FEED_MAX_LIMIT);

        // Fetch all matching requests, rank in Java, then apply the cursor.
        // The table is community-scale (posts), so in-memory ranking is fine.
        List<SkillRequest> candidates = skillRequestRepository.findAll(
                feedCriteria(mine, title, viewer.getId()));

        Instant cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor),
                        java.nio.charset.StandardCharsets.UTF_8);
                int sep = decoded.indexOf('|');
                if (sep > 0) {
                    cursorCreatedAt = Instant.parse(decoded.substring(0, sep));
                    cursorId = Long.valueOf(decoded.substring(sep + 1));
                }
            } catch (IllegalArgumentException e) {
                throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND,
                        "Invalid feed cursor.");
            }
        }
        final Instant cursorTs = cursorCreatedAt;
        final Long cursorIdFinal = cursorId;

        List<SkillRequest> ranked = candidates.stream()
                .sorted(Comparator
                        .comparing(SkillRequest::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(SkillRequest::getId, Comparator.reverseOrder()))
                .toList();

        // Cursor = (createdAt, id) of the last item of the previous page:
        // resume right after that item in the ranked order.
        int start = 0;
        if (cursorTs != null && cursorIdFinal != null) {
            start = ranked.stream()
                    .filter(r -> r.getCreatedAt().equals(cursorTs) && r.getId().equals(cursorIdFinal))
                    .findFirst()
                    .map(r -> ranked.indexOf(r) + 1)
                    .orElse(0);
        }

        List<SkillRequest> page = ranked.stream()
                .skip(start).limit(size).toList();
        boolean hasMore = start + size < ranked.size();

        List<SkillRequestFeedItem> items = buildFeedItems(page, viewer);

        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            SkillRequest last = page.get(page.size() - 1);
            nextCursor = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((last.getCreatedAt().toString() + "|" + last.getId())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new SkillRequestFeedResult(items, nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillRequestFeedItem> recent(int limit, User viewer) {
        int size = limit < 1 ? FEED_DEFAULT_LIMIT : Math.min(limit, FEED_MAX_LIMIT);
        List<SkillRequest> newest = skillRequestRepository.findAll(
                feedCriteria(false, null, null)).stream()
                .sorted(Comparator
                        .comparing(SkillRequest::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(SkillRequest::getId, Comparator.reverseOrder()))
                .limit(size)
                .toList();
        return buildFeedItems(newest, viewer);
    }

    // ------------------------------------------------------------------
    // Detail (single request, same shape as a feed item)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestFeedItem detail(Long requestId, User viewer) {
        SkillRequest request = getExisting(requestId);
        return buildFeedItems(List.of(request), viewer).get(0);
    }

    /**
     * Builds feed items (with comment previews, attachments, vote state)
     * for the given requests. One query each for comments/attachments —
     * no N+1. Shared by {@link #feed} and {@link #detail}.
     */
    private List<SkillRequestFeedItem> buildFeedItems(List<SkillRequest> requests, User viewer) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, User> creators = loadUsersByIds(requests.stream()
                .map(SkillRequest::getCreatedBy).collect(Collectors.toSet()));
        Set<Long> votedIds = loadVotedIds(requests, viewer.getId());

        // Comments of the whole page in one query (no N+1).
        List<Long> requestIds = requests.stream().map(SkillRequest::getId).toList();
        Map<Long, List<SkillRequestComment>> commentsByRequest = commentRepository
                .findByRequestIdInAndDeletedAtIsNull(requestIds).stream()
                .collect(Collectors.groupingBy(SkillRequestComment::getRequestId));
        Map<Long, User> commentUsers = loadUsersByIds(commentsByRequest.values().stream()
                .flatMap(List::stream).map(SkillRequestComment::getUserId).collect(Collectors.toSet()));
        Map<Long, Skill> linkedSkills = loadSkills(commentsByRequest.values().stream()
                .flatMap(List::stream).map(SkillRequestComment::getLinkedSkillId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toSet()));
        Set<Long> likedCommentIds = loadLikedCommentIds(commentsByRequest.values().stream()
                .flatMap(List::stream).map(SkillRequestComment::getId).toList(), viewer.getId());

        // Attachments of the whole page in one query (no N+1).
        Map<Long, List<SkillRequestFile>> filesByRequest = skillRequestFileRepository
                .findByRequestIdIn(requestIds).stream()
                .collect(Collectors.groupingBy(SkillRequestFile::getRequestId));

        return requests.stream().map(r -> {
            List<SkillRequestComment> comments =
                    commentsByRequest.getOrDefault(r.getId(), List.of());
            List<TopCommentItem> topComments = comments.stream()
                    .filter(c -> c.getParentId() == null)
                    .sorted(Comparator.comparing(SkillRequestComment::getCreatedAt).reversed())
                    .limit(TOP_COMMENT_PREVIEW)
                    .map(c -> {
                        Skill sk = c.getLinkedSkillId() != null
                                ? linkedSkills.get(c.getLinkedSkillId()) : null;
                        return new TopCommentItem(
                                c.getId(),
                                c.getUserId(),
                                commentUsers.get(c.getUserId()) != null
                                        ? commentUsers.get(c.getUserId()).getName() : null,
                                commentUsers.get(c.getUserId()) != null
                                        ? commentUsers.get(c.getUserId()).getAvatarUrl() : null,
                                c.getContent(), c.getCreatedAt(),
                                c.getLinkedSkillId(),
                                sk != null ? sk.getName() : null,
                                sk != null ? sk.getApprovalStatus() : null,
                                c.getResolvedYn(),
                                c.getLikeCount() != null ? c.getLikeCount() : 0,
                                likedCommentIds.contains(c.getId()));
                    })
                    .toList();
            boolean hasResolvedComment = comments.stream()
                    .anyMatch(c -> SkillRequestComment.RESOLVED_YES.equals(c.getResolvedYn()));
            // Skills linked in resolved comments, earliest-resolved first (max 3).
            List<SkillRequestComment> resolvedWithSkill = comments.stream()
                    .filter(c -> SkillRequestComment.RESOLVED_YES.equals(c.getResolvedYn()))
                    .filter(c -> c.getLinkedSkillId() != null)
                    .sorted(Comparator.comparing(SkillRequestComment::getResolvedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            int resolvedSkillCount = resolvedWithSkill.size();
            List<ResolvedSkillItem> resolvedSkills = resolvedWithSkill.stream()
                    .limit(RESOLVED_SKILL_PREVIEW)
                    .map(c -> {
                        Skill sk = linkedSkills.get(c.getLinkedSkillId());
                        User commenter = commentUsers.get(c.getUserId());
                        return new ResolvedSkillItem(
                                c.getLinkedSkillId(),
                                sk != null ? sk.getName() : null,
                                sk != null ? sk.getApprovalStatus() : null,
                                commenter != null ? commenter.getName() : null,
                                commenter != null ? commenter.getKnoxId() : null);
                    })
                    .toList();
            List<SkillRequestFileItem> fileItems = filesByRequest
                    .getOrDefault(r.getId(), List.of()).stream()
                    .map(f -> new SkillRequestFileItem(
                            f.getId(), f.getFileName(), f.getFileSize(), f.isImage(),
                            f.isImage() ? skillRequestUrlPrefix + "/" + f.getFilePath() : null))
                    .toList();
            return new SkillRequestFeedItem(
                    r.getId(), r.getTitle(), r.getDescription(),
                    r.getHashtags() != null ? r.getHashtags() : List.of(),
                    r.getVoteCount(), r.getCommentCount(),
                    r.getCreatedBy(),
                    creators.get(r.getCreatedBy()) != null
                            ? creators.get(r.getCreatedBy()).getName() : null,
                    creators.get(r.getCreatedBy()) != null
                            ? creators.get(r.getCreatedBy()).getAvatarUrl() : null,
                    r.getCreatedAt(),
                    votedIds.contains(r.getId()),
                    r.getCreatedBy().equals(viewer.getId()),
                    hasResolvedComment,
                    resolvedSkills,
                    resolvedSkillCount,
                    topComments,
                    fileItems,
                    r.getBountyPoints() != null ? r.getBountyPoints() : 0,
                    r.getBountyStatus(),
                    r.getBountyExpiresAt(),
                    r.getFulfilledSkillId());
        }).toList();
    }

    /** Feed ranking score: (vote*2 + comment*1) * time decay. */
    private static double scoreOf(SkillRequest r) {
        double decay;
        if (Duration.between(r.getCreatedAt(), Instant.now()).compareTo(DECAY_TIER_1) <= 0) {
            decay = DECAY_RECENT;
        } else if (Duration.between(r.getCreatedAt(), Instant.now()).compareTo(DECAY_TIER_2) <= 0) {
            decay = DECAY_MID;
        } else {
            decay = DECAY_OLD;
        }
        return (r.getVoteCount() * VOTE_WEIGHT + r.getCommentCount() * COMMENT_WEIGHT) * decay;
    }

    private Specification<SkillRequest> feedCriteria(boolean mine, String title, Long viewerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (mine) {
                predicates.add(cb.equal(root.get("createdBy"), viewerId));
            }
            if (title != null && !title.isBlank()) {
                String kw = title.trim().toLowerCase(Locale.ROOT);
                // Hashtags are stored without the "#" prefix, so accept both
                // "#tag" and "tag" in the search box.
                String tagKw = kw.startsWith("#") ? kw.substring(1) : kw;
                // jsonb -> text via the text() function: Hibernate does not
                // render Path.as(String.class) as a CAST, and PostgreSQL has
                // no lower(jsonb) (same pattern as SkillServiceImpl).
                Expression<String> hashtagsAsText = cb.lower(
                        cb.function("text", String.class, root.get("hashtags")));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), "%" + kw + "%"),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), "%" + kw + "%"),
                        cb.like(hashtagsAsText, "%" + tagKw + "%")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(SkillRequestCreateRequest request, MultipartFile[] files, User creator) {
        if (!rateLimiter.allow("skill-request-create:" + creator.getId(),
                CREATE_MAX_PER_MINUTE, CREATE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }

        String trimmedTitle = request.title() == null ? "" : request.title().trim();
        if (trimmedTitle.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_TITLE_REQUIRED,
                    "Request title is required.");
        }
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_TITLE_TOO_LONG,
                    "Request title is at most 100 characters.");
        }
        List<String> hashtags = parseAndValidateHashtags(request.hashtags());
        int fileCount = files == null ? 0 : files.length;
        if (fileCount > MAX_FILES) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILES_TOO_MANY,
                    "At most 10 attachments are allowed.");
        }
        long totalSize = files == null ? 0
                : java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty())
                        .mapToLong(MultipartFile::getSize).sum();
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_TOO_LARGE,
                    "Total attachments are at most 100 MB.");
        }

        SkillRequest entity = skillRequestRepository.save(SkillRequest.builder()
                .title(trimmedTitle)
                .description(request.description() == null || request.description().isBlank()
                        ? null : request.description())
                .hashtags(hashtags)
                .createdBy(creator.getId())
                .build());

        storeRequestFiles(entity.getId(), files);
        return entity.getId();
    }

    /** Stores the uploaded attachments of a freshly created request. */
    private void storeRequestFiles(Long requestId, MultipartFile[] files) {
        if (files == null) {
            return;
        }
        List<SkillRequestFile> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String relativePath = skillRequestFileStorageService.store(requestId, file);
            stored.add(SkillRequestFile.builder()
                    .requestId(requestId)
                    .fileName(file.getOriginalFilename() == null ? "" : file.getOriginalFilename())
                    .filePath(relativePath)
                    .fileSize(file.getSize())
                    .isImage(skillRequestFileStorageService.isImage(file.getOriginalFilename()))
                    .build());
        }
        if (!stored.isEmpty()) {
            skillRequestFileRepository.saveAll(stored);
        }
    }

    private List<String> parseAndValidateHashtags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tag : rawTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_HASHTAG_LENGTH) {
                throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_HASHTAG_TOO_LONG,
                        "Each hashtag is at most 30 characters.");
            }
            if (!HASHTAG_PATTERN.matcher(trimmed).matches()) {
                throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_HASHTAG_INVALID,
                        "Hashtags may only contain letters, numbers, underscore and hyphen.");
            }
            if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue; // case-insensitive dedup
            }
            result.add(trimmed);
        }
        if (result.size() > MAX_HASHTAGS) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_HASHTAGS_TOO_MANY,
                    "At most 10 hashtags are allowed.");
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Vote / unvote (toggle)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void vote(Long requestId, User user) {
        if (!rateLimiter.allow("skill-request-vote:" + user.getId(),
                VOTE_MAX_PER_MINUTE, VOTE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        SkillRequest request = getExisting(requestId);
        if (voteRepository.existsByRequestIdAndUserId(requestId, user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_ALREADY_VOTED,
                    "You have already voted for this request.");
        }
        voteRepository.save(SkillRequestVote.builder()
                .requestId(requestId)
                .userId(user.getId())
                .build());
        request.setVoteCount(request.getVoteCount() + 1);
        skillRequestRepository.save(request);
        notifyForumEvent(request, user,
                "voted for your request \"" + request.getTitle() + "\".",
                "voted for your skill request \"" + request.getTitle() + "\".");
    }

    @Override
    @Transactional
    public void unvote(Long requestId, User user) {
        if (!rateLimiter.allow("skill-request-vote:" + user.getId(),
                VOTE_MAX_PER_MINUTE, VOTE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        SkillRequest request = getExisting(requestId);
        if (!voteRepository.existsByRequestIdAndUserId(requestId, user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND,
                    "You have not voted for this request.");
        }
        voteRepository.deleteByRequestIdAndUserId(requestId, user.getId());
        request.setVoteCount(Math.max(0, request.getVoteCount() - 1));
        skillRequestRepository.save(request);
    }

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestCommentPage listComments(Long requestId, User viewer, int page, int size) {
        getExisting(requestId);
        int p = page < 1 ? 1 : page;
        int sz = size < 1 ? 10 : size;

        // All non-deleted comments of the request (to assemble reply trees).
        List<SkillRequestComment> all =
                commentRepository.findByRequestIdAndDeletedAtIsNull(requestId);
        Map<Long, List<SkillRequestComment>> childrenByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .sorted(Comparator.comparing(SkillRequestComment::getCreatedAt))
                .collect(Collectors.groupingBy(SkillRequestComment::getParentId));

        // Top-level comments in chronological order (oldest first); replies
        // stay nested under their parent.
        List<SkillRequestComment> topLevel = all.stream()
                .filter(c -> c.getParentId() == null)
                .sorted(Comparator
                        .comparing(SkillRequestComment::getCreatedAt)
                        .thenComparing(SkillRequestComment::getId))
                .toList();
        int total = topLevel.size();
        int from = Math.min((p - 1) * sz, total);
        List<SkillRequestComment> pageComments = topLevel.subList(from, Math.min(from + sz, total));

        Map<Long, User> users = loadUsersByIds(all.stream()
                .map(SkillRequestComment::getUserId).collect(Collectors.toSet()));
        Map<Long, Skill> linkedSkills = loadSkills(all.stream()
                .map(SkillRequestComment::getLinkedSkillId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toSet()));
        Set<Long> likedIds = loadLikedCommentIds(all.stream()
                .map(SkillRequestComment::getId).toList(), viewer.getId());
        boolean isAdmin = isAdmin(viewer);
        List<SkillRequestCommentItem> items = pageComments.stream()
                .map(c -> toCommentItem(c, users, linkedSkills, likedIds, isAdmin,
                        viewer.getId(), 1, childrenByParent))
                .toList();
        return new SkillRequestCommentPage(total, p, sz, items);
    }

    /** Builds a comment DTO with its reply tree nested (max 3 levels). */
    private SkillRequestCommentItem toCommentItem(
            SkillRequestComment c,
            Map<Long, User> users,
            Map<Long, Skill> linkedSkills,
            Set<Long> likedIds,
            boolean isAdmin,
            Long viewerId,
            int depth,
            Map<Long, List<SkillRequestComment>> childrenByParent) {
        Skill sk = c.getLinkedSkillId() != null ? linkedSkills.get(c.getLinkedSkillId()) : null;
        List<SkillRequestCommentItem> replies = List.of();
        if (depth < MAX_COMMENT_DEPTH) {
            replies = childrenByParent.getOrDefault(c.getId(), List.of()).stream()
                    .map(child -> toCommentItem(child, users, linkedSkills, likedIds,
                            isAdmin, viewerId, depth + 1, childrenByParent))
                    .toList();
        }
        return new SkillRequestCommentItem(
                c.getId(), c.getUserId(),
                users.get(c.getUserId()) != null ? users.get(c.getUserId()).getName() : null,
                users.get(c.getUserId()) != null ? users.get(c.getUserId()).getAvatarUrl() : null,
                c.getContent(), c.getCreatedAt(),
                isAdmin || c.getUserId().equals(viewerId),
                c.getUserId().equals(viewerId),
                c.getParentId(),
                c.getLinkedSkillId(),
                sk != null ? sk.getName() : null,
                sk != null ? sk.getApprovalStatus() : null,
                c.getResolvedYn(), c.getResolvedAt(),
                c.getLikeCount() != null ? c.getLikeCount() : 0,
                likedIds.contains(c.getId()),
                replies);
    }

    @Override
    @Transactional
    public void addComment(Long requestId, User user, String content, Long linkedSkillId, Long parentId) {
        if (!rateLimiter.allow("skill-request-comment:" + user.getId(),
                COMMENT_MAX_PER_MINUTE, COMMENT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        SkillRequest request = getExisting(requestId);
        // Raw-input size guard BEFORE sanitizing: the sanitized-output cap
        // below cannot protect the Jsoup parse of an unbounded payload
        // (same policy as AI News comments).
        if (content != null && content.length() > MAX_COMMENT_HTML_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment content is too large.");
        }
        // Rich text: sanitize the editor HTML, then validate the PLAIN TEXT
        // (HTML tags and embedded images don't count toward the limit).
        String sanitized = commentHtmlSanitizer.sanitize(content);
        String plainText = Jsoup.parse(sanitized == null ? "" : sanitized).text().trim();
        if (plainText.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (plainText.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment exceeds 1000 characters.");
        }
        if (linkedSkillId != null && !skillRepository.existsById(linkedSkillId)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_LINK_INVALID,
                    "Linked skill not found.");
        }
        if (linkedSkillId != null && request.getCreatedBy().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_LINK_INVALID,
                    "The request creator cannot link a skill to their own request.");
        }
        // Threading: the parent must belong to this request and not exceed depth 3.
        SkillRequestComment parent = null;
        if (parentId != null) {
            parent = commentRepository.findById(parentId).orElse(null);
            if (parent == null || !parent.getRequestId().equals(requestId)
                    || parent.getDeletedAt() != null) {
                throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_NOT_FOUND,
                        "Parent comment not found.");
            }
            int parentDepth = depthOf(parent);
            if (parentDepth >= MAX_COMMENT_DEPTH) {
                throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_DEPTH_EXCEEDED,
                        "Replies are limited to 3 levels.");
            }
        }
        SkillRequestComment saved = commentRepository.save(SkillRequestComment.builder()
                .requestId(requestId)
                .userId(user.getId())
                .content(sanitized)
                .linkedSkillId(linkedSkillId)
                .parentId(parentId)
                .build());
        request.setCommentCount(request.getCommentCount() + 1);
        skillRequestRepository.save(request);

        // Notify: request creator (top-level or reply) + parent comment author (reply).
        String action = parent == null ? "commented on your request" : "replied to a comment on your request";
        notifyForumEvent(request, user,
                action + " \"" + request.getTitle() + "\".",
                action + " \"" + request.getTitle() + "\".");
        if (parent != null && !parent.getUserId().equals(user.getId())) {
            notifyUser(user, parent.getUserId(),
                    "replied to your comment on \"" + request.getTitle() + "\".",
                    "replied to your comment on \"" + request.getTitle() + "\".",
                    requestId, parent.getId());
        }

        // Best-effort @mention notifications — must never break the comment.
        // Runs AFTER the comment transaction commits (afterCommit) so the
        // SMTP round-trips (up to 10 emails) don't hold the comment's DB
        // connection open. The callback itself opens a NEW transaction
        // (notificationTransaction). When no transaction is active (unit
        // tests) it runs inline.
        try {
            runAfterCommit(() -> {
                try {
                    notificationTransaction.executeWithoutResult(
                            status -> notifyMentionedUsers(user, saved, request.getTitle(),
                                    parseMentionUserIds(saved.getContent())));
                } catch (Exception ex) {
                    log.error("Skill Market: @mention notification callback failed for comment {}: {}",
                            saved.getId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.warn("Skill Market: could not send @mention notifications for comment {}: {}",
                    saved.getId(), e.getMessage());
        }
    }

    /**
     * Runs the action after the current transaction commits (so slow side
     * effects — SMTP sends — don't stretch the DB transaction). When no
     * transaction synchronization is active (e.g. unit tests) the action
     * runs inline, preserving the previous behavior.
     */
    private void runAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    /** Depth of a comment in its thread (top-level = 1). */
    private int depthOf(SkillRequestComment comment) {
        int depth = 1;
        SkillRequestComment current = comment;
        // Guard against accidental cycles: stop after a reasonable bound.
        while (current.getParentId() != null && depth < 64) {
            SkillRequestComment parent = commentRepository.findById(current.getParentId()).orElse(null);
            if (parent == null) break;
            current = parent;
            depth++;
        }
        return depth;
    }

    @Override
    @Transactional
    public void likeComment(Long requestId, Long commentId, User user) {
        SkillRequest request = getExisting(requestId);
        SkillRequestComment comment = getExistingComment(requestId, commentId);
        if (likeRepository.existsByCommentIdAndUserId(commentId, user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_ALREADY_LIKED,
                    "You already liked this comment.");
        }
        likeRepository.save(SkillRequestCommentLike.builder()
                .commentId(commentId).userId(user.getId()).build());
        comment.setLikeCount((comment.getLikeCount() != null ? comment.getLikeCount() : 0) + 1);
        commentRepository.save(comment);
        if (!comment.getUserId().equals(user.getId())) {
            notifyUser(user, comment.getUserId(),
                    "liked your comment on \"" + request.getTitle() + "\".",
                    "liked your comment on \"" + request.getTitle() + "\".",
                    requestId, commentId);
        }
    }

    @Override
    @Transactional
    public void unlikeComment(Long requestId, Long commentId, User user) {
        getExisting(requestId);
        SkillRequestComment comment = getExistingComment(requestId, commentId);
        if (!likeRepository.existsByCommentIdAndUserId(commentId, user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_NOT_FOUND,
                    "You have not liked this comment.");
        }
        likeRepository.deleteByCommentIdAndUserId(commentId, user.getId());
        int count = comment.getLikeCount() != null ? comment.getLikeCount() : 0;
        comment.setLikeCount(Math.max(0, count - 1));
        commentRepository.save(comment);
    }

    private SkillRequestComment getExistingComment(Long requestId, Long commentId) {
        SkillRequestComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getRequestId().equals(requestId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_NOT_FOUND,
                    "Comment not found.");
        }
        return comment;
    }

    private Set<Long> loadLikedCommentIds(List<Long> commentIds, Long userId) {
        if (commentIds.isEmpty()) {
            return Set.of();
        }
        return likeRepository.findByCommentIdIn(commentIds).stream()
                .filter(l -> l.getUserId().equals(userId))
                .map(SkillRequestCommentLike::getCommentId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void deleteComment(Long requestId, Long commentId, User user) {
        getExisting(requestId);
        SkillRequestComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getRequestId().equals(requestId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_NOT_FOUND,
                    "Comment not found.");
        }
        boolean isAdmin = isAdmin(user);
        if (!isAdmin && !comment.getUserId().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "You can only delete your own comments.");
        }
        // Soft-delete the comment and its whole reply subtree.
        Map<Long, List<SkillRequestComment>> childrenByParent =
                commentRepository.findByRequestIdAndDeletedAtIsNull(requestId).stream()
                        .filter(c -> c.getParentId() != null)
                        .collect(Collectors.groupingBy(SkillRequestComment::getParentId));
        List<SkillRequestComment> subtree = new ArrayList<>();
        subtree.add(comment);
        collectDescendants(commentId, childrenByParent, subtree);
        Instant now = Instant.now();
        for (SkillRequestComment c : subtree) {
            c.setDeletedAt(now);
            commentRepository.save(c);
        }

        SkillRequest request = getExisting(requestId);
        if (request.getCommentCount() > 0) {
            request.setCommentCount(Math.max(0, request.getCommentCount() - subtree.size()));
            skillRequestRepository.save(request);
        }
    }

    @Override
    @Transactional
    public void updateComment(Long requestId, Long commentId, String content, User user) {
        SkillRequestComment comment = getExistingComment(requestId, commentId);
        if (!comment.getUserId().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "You can only edit your own comments.");
        }
        if (content != null && content.length() > MAX_COMMENT_HTML_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment content is too large.");
        }
        String sanitized = commentHtmlSanitizer.sanitize(content);
        String plainText = Jsoup.parse(sanitized == null ? "" : sanitized).text().trim();
        if (plainText.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_EMPTY,
                    "Comment cannot be empty.");
        }
        if (plainText.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_COMMENT_TOO_LONG,
                    "Comment exceeds 1000 characters.");
        }
        // Only @mention users NEWLY added by this edit (diff vs the old
        // content) — re-notifying users already mentioned on every edit
        // would spam them with duplicate notifications.
        Set<Long> newlyMentioned = new LinkedHashSet<>(parseMentionUserIds(sanitized));
        newlyMentioned.removeAll(parseMentionUserIds(comment.getContent()));
        comment.setContent(sanitized);
        commentRepository.save(comment);

        if (!newlyMentioned.isEmpty()) {
            SkillRequest request = getExisting(requestId);
            try {
                runAfterCommit(() -> {
                    try {
                        notificationTransaction.executeWithoutResult(
                                status -> notifyMentionedUsers(user, comment, request.getTitle(),
                                        newlyMentioned));
                    } catch (Exception ex) {
                        log.error("Skill Market: @mention notification callback failed for comment {}: {}",
                                comment.getId(), ex.getMessage(), ex);
                    }
                });
            } catch (Exception e) {
                log.warn("Skill Market: could not send @mention notifications for comment {}: {}",
                        comment.getId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // @mention notifications (same pipeline as AI News comments)
    // ------------------------------------------------------------------

    /**
     * Sends in-app + email notifications to the given @mention recipients
     * (best-effort — callers catch exceptions). The actor is never notified
     * about their own mention. Recipients are capped at
     * {@link #MAX_MENTIONS_PER_COMMENT} (anti-spam — the ids are
     * client-supplied).
     *
     * @param recipients the user ids to notify (addComment: everyone mentioned
     *                   in the new comment; updateComment: only the users
     *                   NEWLY mentioned by the edit)
     */
    private void notifyMentionedUsers(User actor, SkillRequestComment comment, String requestTitle,
                                      Set<Long> recipients) {
        Set<Long> mentionedIds = recipients;
        if (mentionedIds.size() > MAX_MENTIONS_PER_COMMENT) {
            log.warn("Skill Market: comment {} mentions {} distinct users — capping at {}",
                    comment.getId(), mentionedIds.size(), MAX_MENTIONS_PER_COMMENT);
        }
        // Batch-fetch users + settings upfront (up to 10 ids).
        Map<Long, User> userMap = userRepository.findAllById(mentionedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, UserSettings> settingsMap = userSettingsRepository.findAllById(mentionedIds).stream()
                .collect(Collectors.toMap(UserSettings::getUserId, s -> s, (a, b) -> a));

        int notified = 0;
        for (Long recipientId : mentionedIds) {
            if (notified >= MAX_MENTIONS_PER_COMMENT) {
                break;
            }
            User recipient = userMap.get(recipientId);
            if (recipient == null || !recipient.isEnabled()) {
                continue;
            }
            String actorName = actor.getName() != null ? actor.getName() : "Someone";
            String requestUrl = requestDetailUrl(comment.getRequestId());
            String emailBody = actorName + " mentioned you in \"" + requestTitle + "\"."
                    + (requestUrl != null ? "\n\n" + requestUrl : "");
            boolean sent = eventNotificationService.notify(actor, recipient,
                    settingsMap.get(recipientId),
                    new EventNotificationService.EventDetails(
                            actorName + " mentioned you in: " + requestTitle,
                            new EventNotificationService.EventContext(
                                    null, null, comment.getRequestId(), null, comment.getId()),
                            "[AX Portal] Skill Market: " + actorName + " mentioned you",
                            emailBody));
            // Count only ACTUAL notifications — skipped recipients must not
            // consume the anti-spam budget.
            if (sent) {
                notified++;
            }
        }
    }

    /** Extracts data-user-id values from mention spans (deduped, invalid ids dropped). */
    private Set<Long> parseMentionUserIds(String html) {
        Set<Long> ids = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return ids;
        }
        for (Element span :
                Jsoup.parseBodyFragment(html).select("span[data-type=mention][data-user-id]")) {
            try {
                ids.add(Long.parseLong(span.attr("data-user-id").trim()));
            } catch (NumberFormatException ignored) {
                // malformed id — skip
            }
        }
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentionUserItem> mentionUsers(String query) {
        String q = query == null ? null : query.trim();
        Specification<User> spec = (root, cq, cb) -> {
            var criteria = cb.equal(root.get("enabled"), true);
            if (q != null && !q.isEmpty()) {
                // Escape LIKE wildcards so a query of "%" or "a_b" matches
                // literally (same policy as AI News).
                String like = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
                criteria = cb.and(criteria, cb.or(
                        cb.like(cb.lower(root.get("name")), like, '/'),
                        cb.like(cb.lower(root.get("email")), like, '/')));
            }
            return criteria;
        };
        return userRepository.findAll(spec,
                        PageRequest.of(0, MENTION_SEARCH_LIMIT,
                                Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"))))
                .stream()
                .map(u -> new MentionUserItem(u.getId(), u.getName()))
                .toList();
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("/", "//").replace("%", "/%").replace("_", "/_");
    }

    // ------------------------------------------------------------------
    // Forum notifications (in-app bell + email, per UserSettings)
    // ------------------------------------------------------------------

    /** Notifies the request creator (skipped when the actor is the creator). */
    private void notifyForumEvent(SkillRequest request, User actor, String inappSuffix, String emailSuffix) {
        if (request.getCreatedBy().equals(actor.getId())) {
            return;
        }
        notifyUser(actor, request.getCreatedBy(), inappSuffix, emailSuffix, request.getId(), null);
    }

    /**
     * Creates an in-app notification (and sends an email) for a forum event,
     * honoring the recipient's notification settings. Never notifies the actor
     * themselves; failures are logged and never break the main action.
     */
    private void notifyUser(User actor, Long recipientId, String inappSuffix, String emailSuffix,
                            Long requestId, Long commentId) {
        if (recipientId.equals(actor.getId())) {
            return;
        }
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null || !recipient.isEnabled()) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(recipientId)
                .orElse(UserSettings.builder().userId(recipientId)
                        .inappNotify(true).emailNotify(true).build());
        String actorName = actor.getName() != null ? actor.getName() : "Someone";
        String requestUrl = requestDetailUrl(requestId);
        String emailBody = actorName + " " + emailSuffix
                + (requestUrl != null ? "\n\n" + requestUrl : "");
        eventNotificationService.notify(actor, recipient, settings,
                new EventNotificationService.EventDetails(
                        actorName + " " + inappSuffix,
                        new EventNotificationService.EventContext(
                                null, null, requestId, null, commentId),
                        "[AX Portal] Skill Market: " + actorName + " " + emailSuffix,
                        emailBody));
    }

    /** Frontend URL of the skill request detail page. */
    private String requestDetailUrl(Long requestId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/skill-market/" + requestId;
    }

    /** Collects all descendants of a comment (DFS over the preloaded child map). */
    private void collectDescendants(Long parentId,
                                    Map<Long, List<SkillRequestComment>> childrenByParent,
                                    List<SkillRequestComment> out) {
        for (SkillRequestComment child : childrenByParent.getOrDefault(parentId, List.of())) {
            out.add(child);
            collectDescendants(child.getId(), childrenByParent, out);
        }
    }

    @Override
    @Transactional
    public void resolveComment(Long requestId, Long commentId, User user) {
        SkillRequest request = getExisting(requestId);
        SkillRequestComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || !comment.getRequestId().equals(requestId)
                || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_COMMENT_NOT_FOUND,
                    "Comment not found.");
        }
        boolean isCreator = request.getCreatedBy().equals(user.getId());
        if (!isCreator && !isAdmin(user)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "Only the request creator can resolve comments.");
        }
        if (comment.getLinkedSkillId() == null) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "Only comments with a linked skill can be resolved.");
        }
        // Idempotent: resolving twice is a no-op.
        if (!SkillRequestComment.RESOLVED_YES.equals(comment.getResolvedYn())) {
            comment.setResolvedYn(SkillRequestComment.RESOLVED_YES);
            comment.setResolvedAt(Instant.now());
            comment.setResolvedBy(user.getId());
            commentRepository.save(comment);
        }
    }

    // ------------------------------------------------------------------
    // Duplicate suggestions (option A)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestSuggestions suggestions(String keyword, User viewer) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) {
            return new SkillRequestSuggestions(List.of(), List.of());
        }
        String like = "%" + kw + "%";

        List<Skill> skills = skillRepository.findAll((root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)))
                .stream()
                .filter(s -> !"Y".equals(s.getDeletedYn()))
                .sorted(Comparator.comparing(Skill::getCreatedAt).reversed())
                .limit(SUGGESTION_LIMIT)
                .toList();
        Map<Long, User> skillCreators = loadUsersByIds(skills.stream()
                .map(Skill::getCreatedBy).collect(Collectors.toSet()));
        List<SkillRequestSuggestions.SkillSuggestion> skillSuggestions = skills.stream()
                .map(s -> new SkillRequestSuggestions.SkillSuggestion(
                        s.getId(), s.getName(), s.getApprovalStatus(),
                        skillCreators.get(s.getCreatedBy()) != null
                                ? skillCreators.get(s.getCreatedBy()).getName() : null))
                .toList();

        List<SkillRequest> requests = skillRequestRepository.findAll((root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)))
                .stream()
                .sorted(Comparator.comparing(SkillRequest::getCreatedAt).reversed())
                .limit(SUGGESTION_LIMIT)
                .toList();
        List<SkillRequestSuggestions.RequestSuggestion> requestSuggestions = requests.stream()
                .map(r -> new SkillRequestSuggestions.RequestSuggestion(
                        r.getId(), r.getTitle(), r.getVoteCount()))
                .toList();

        return new SkillRequestSuggestions(skillSuggestions, requestSuggestions);
    }

    // ------------------------------------------------------------------
    // Sidebar stats (top-10 cards)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestStatsResult stats() {
        List<SkillRequest> all = skillRequestRepository.findAll();
        Instant now = Instant.now();

        // Top voted (last 7 days): voteCount desc, then commentCount desc, then newest.
        List<SkillRequestStatsResult.TopVotedItem> topVoted = all.stream()
                .filter(r -> r.getCreatedAt() != null
                        && !r.getCreatedAt().isBefore(now.minus(TOP_VOTED_WINDOW)))
                .sorted(Comparator
                        .comparing(SkillRequest::getVoteCount, Comparator.reverseOrder())
                        .thenComparing(SkillRequest::getCommentCount, Comparator.reverseOrder())
                        .thenComparing(SkillRequest::getCreatedAt, Comparator.reverseOrder()))
                .limit(STATS_TOP_LIMIT)
                .map(r -> new SkillRequestStatsResult.TopVotedItem(
                        r.getId(), r.getTitle(), r.getVoteCount(), r.getCommentCount()))
                .toList();

        // Top contributors (all time): users who linked the most skills into comments.
        Map<Long, Long> linkCounts = commentRepository.countSkillLinksByUser().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        Long::sum));
        Map<Long, User> users = loadUsersByIds(linkCounts.keySet());
        Comparator<Map.Entry<Long, Long>> byLinkCount =
                Comparator.comparingLong(Map.Entry<Long, Long>::getValue).reversed();
        Comparator<Map.Entry<Long, Long>> byUserName =
                Comparator.comparing(e -> {
                    User u = users.get(e.getKey());
                    return u != null && u.getName() != null ? u.getName() : "";
                });
        List<SkillRequestStatsResult.TopContributorItem> topContributors = linkCounts.entrySet().stream()
                .sorted(byLinkCount.thenComparing(byUserName))
                .limit(CONTRIBUTOR_TOP_LIMIT)
                .map(e -> new SkillRequestStatsResult.TopContributorItem(
                        e.getKey(),
                        users.get(e.getKey()) != null ? users.get(e.getKey()).getName() : null,
                        users.get(e.getKey()) != null ? users.get(e.getKey()).getAvatarUrl() : null,
                        e.getValue().intValue()))
                .toList();

        // Popular hashtags (last 30 days): hashtag count desc, then tag name.
        Map<String, Long> tagCounts = all.stream()
                .filter(r -> r.getCreatedAt() != null
                        && !r.getCreatedAt().isBefore(now.minus(HASHTAG_WINDOW)))
                .filter(r -> r.getHashtags() != null)
                .flatMap(r -> r.getHashtags().stream())
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.groupingBy(
                        t -> t.trim().toLowerCase(Locale.ROOT), Collectors.counting()));
        Comparator<Map.Entry<String, Long>> byTagCount =
                Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed();
        Comparator<Map.Entry<String, Long>> byTagName = Comparator.comparing(Map.Entry::getKey);
        List<SkillRequestStatsResult.PopularHashtagItem> popularHashtags = tagCounts.entrySet().stream()
                .sorted(byTagCount.thenComparing(byTagName))
                .limit(STATS_TOP_LIMIT)
                .map(e -> new SkillRequestStatsResult.PopularHashtagItem(e.getKey(), e.getValue().intValue()))
                .toList();

        return new SkillRequestStatsResult(topVoted, topContributors, popularHashtags);
    }

    // ------------------------------------------------------------------
    // File attachments (download)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillRequestFile getFile(Long requestId, Long fileId) {
        getExisting(requestId);
        return skillRequestFileRepository.findById(fileId)
                .filter(f -> f.getRequestId().equals(requestId))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_NOT_FOUND,
                        "Skill request file does not exist."));
    }

    // ------------------------------------------------------------------
    // Update / Delete (creator only)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long update(Long requestId, SkillRequestCreateRequest request, MultipartFile[] files,
                       List<Long> removeFileIds, User user) {
        SkillRequest existing = getExisting(requestId);
        if (!existing.getCreatedBy().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "You can only edit your own requests.");
        }

        String trimmedTitle = request.title() == null ? "" : request.title().trim();
        if (trimmedTitle.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_TITLE_REQUIRED,
                    "Request title is required.");
        }
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_TITLE_TOO_LONG,
                    "Request title is at most 100 characters.");
        }
        List<String> hashtags = parseAndValidateHashtags(request.hashtags());

        // Remove existing files (creator only — verified above).
        if (removeFileIds != null && !removeFileIds.isEmpty()) {
            for (SkillRequestFile f : skillRequestFileRepository.findByRequestId(requestId)) {
                if (removeFileIds.contains(f.getId())) {
                    skillRequestFileStorageService.delete(f.getFilePath());
                    skillRequestFileRepository.delete(f);
                }
            }
        }

        // Append new files (if any) to the remaining ones.
        List<SkillRequestFile> remainingFiles = skillRequestFileRepository.findByRequestId(requestId);
        int existingFileCount = remainingFiles.size();
        int newFileCount = files == null ? 0
                : (int) java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).count();
        if (existingFileCount + newFileCount > MAX_FILES) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILES_TOO_MANY,
                    "At most 10 attachments are allowed.");
        }
        long totalSize = remainingFiles.stream()
                .mapToLong(f -> f.getFileSize() == null ? 0L : f.getFileSize()).sum()
                + (files == null ? 0L : java.util.Arrays.stream(files)
                        .filter(f -> f != null && !f.isEmpty())
                        .mapToLong(MultipartFile::getSize).sum());
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_TOO_LARGE,
                    "Total attachments are at most 100 MB.");
        }

        existing.setTitle(trimmedTitle);
        existing.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description());
        existing.setHashtags(hashtags);
        skillRequestRepository.save(existing);

        storeRequestFiles(requestId, files);
        return requestId;
    }

    @Override
    @Transactional
    public void delete(Long requestId, User user) {
        SkillRequest existing = getExisting(requestId);
        if (!existing.getCreatedBy().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_EDITABLE,
                    "You can only delete your own requests.");
        }

        // Delete physical files from disk first.
        for (SkillRequestFile f : skillRequestFileRepository.findByRequestId(requestId)) {
            skillRequestFileStorageService.delete(f.getFilePath());
        }

        // Delete comments (DB cascade handles comment_likes), then votes.
        commentRepository.deleteByRequestId(requestId);
        voteRepository.deleteByRequestId(requestId);

        // Finally delete the request (DB cascade handles file rows).
        skillRequestRepository.delete(existing);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SkillRequest getExisting(Long requestId) {
        return skillRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND,
                        "Skill request not found."));
    }

    private Map<Long, User> loadUsersByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, Skill> loadSkills(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
    }

    private Set<Long> loadVotedIds(List<SkillRequest> requests, Long viewerId) {
        if (requests.isEmpty()) {
            return Set.of();
        }
        Set<Long> requestIds = requests.stream().map(SkillRequest::getId).collect(Collectors.toSet());
        return voteRepository.findByUserId(viewerId).stream()
                .map(SkillRequestVote::getRequestId)
                .filter(requestIds::contains)
                .collect(Collectors.toSet());
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }
}
