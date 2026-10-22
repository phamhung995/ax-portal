package com.skillmanagement.skill.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.mail.EmailService;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.skill.model.*;
import com.skillmanagement.skill.model.SkillCategory;
import com.skillmanagement.skill.model.SkillStageMapping;
import com.skillmanagement.skill.model.SkillVersion;
import com.skillmanagement.skill.model.SkillVersionFile;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.skill.repository.*;
import com.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.skillmanagement.user.model.UserSettings;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.skill.service.ExcelExportStyle;
import com.skillmanagement.common.RateLimiter;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.SkillFileStorageService;
import com.skillmanagement.skill.service.SkillService;
import com.skillmanagement.skill.service.SkillVersionUpdateService;
import com.skillmanagement.skill.service.SkillWorkflowService;
import com.skillmanagement.notification.model.Notification;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core Skill Management implementation (Skill Management SRS).
 * Visibility (status + scope) is enforced by
 * {@link SkillRepository#visibleTo} on every read path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_HASHTAGS = 10;
    private static final int MAX_HASHTAG_LENGTH = 30;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            Skill.SCOPE_PRIVATE, Skill.SCOPE_PART, Skill.SCOPE_GROUP, Skill.SCOPE_PUBLIC);
    private static final int CREATE_MAX_PER_MINUTE = 5;
    private static final int CREATE_WINDOW_SECONDS = 60;

    /** Minutes between two counted views of the same skill by the same user. */
    @org.springframework.beans.factory.annotation.Value("${app.skill.view-window-minutes:15}")
    private int viewWindowMinutes;

    private final SkillRepository skillRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillStatusHistoryRepository skillStatusHistoryRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final SkillViewLogRepository skillViewLogRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final ProcessStageRepository processStageRepository;
    private final SkillCategoryMappingRepository skillCategoryMappingRepository;
    private final SkillStageMappingRepository skillStageMappingRepository;
    private final SkillAccessHelper skillAccessHelper;
    private final SkillWorkflowService skillWorkflowService;
    private final SkillVersionUpdateService skillVersionUpdateService;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillVersionFileRepository skillVersionFileRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SkillFileStorageService skillFileStorageService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EmailService emailService;

    // ------------------------------------------------------------------
    // Create (Add SRS §4)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(String name, String description, String publicScope,
                       String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                       MultipartFile[] files, User creator) {
        if (!rateLimiter.allow("skill-create:" + creator.getId(),
                CREATE_MAX_PER_MINUTE, CREATE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_REQUIRED,
                    "Skill name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_TOO_LONG,
                    "Skill name is at most 100 characters.");
        }
        if (skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy(trimmedName, "N", creator.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_DUPLICATE,
                    "You already have a skill with this name.");
        }
        if (publicScope == null || !ALLOWED_SCOPES.contains(publicScope)) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_SCOPE,
                    "Invalid public scope.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);
        long fileCount = files == null ? 0
                : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).count();
        if (fileCount == 0) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_REQUIRED,
                    "At least one file is required.");
        }

        Skill skill = Skill.builder()
                .name(trimmedName)
                .description(description == null || description.isBlank() ? null : description)
                .approvalStatus(Skill.STATUS_DRAFT)
                .publicScope(publicScope)
                .hashtags(hashtags)
                .createdBy(creator.getId())
                .build();
        Skill saved = skillRepository.save(skill);
        storeFiles(saved.getId(), files);
        storeCategoryMappings(saved.getId(), categories);
        storeStageMappings(saved.getId(), stages);

        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(saved.getId())
                .fromStatus(null)
                .toStatus(Skill.STATUS_DRAFT)
                .changedBy(creator.getId())
                .build());

        return saved.getId();
    }

    private List<String> parseAndValidateHashtags(String hashtagsJson) {
        if (hashtagsJson == null || hashtagsJson.isBlank()) {
            return List.of();
        }
        List<String> raw;
        try {
            raw = objectMapper.readValue(hashtagsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_HASHTAG,
                    "Invalid hashtags payload.");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tag : raw) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_HASHTAG_LENGTH) {
                throw new BusinessException(BusinessErrorCode.SKILL_HASHTAG_TOO_LONG,
                        "Each hashtag is at most 30 characters.");
            }
            if (!HASHTAG_PATTERN.matcher(trimmed).matches()) {
                throw new BusinessException(BusinessErrorCode.SKILL_INVALID_HASHTAG,
                        "Hashtags may only contain letters, numbers, underscore and hyphen.");
            }
            if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue; // case-insensitive dedup
            }
            result.add(trimmed);
        }
        if (result.size() > MAX_HASHTAGS) {
            throw new BusinessException(BusinessErrorCode.SKILL_HASHTAGS_TOO_MANY,
                    "At most 10 hashtags are allowed.");
        }
        return result;
    }

    /** Stores the given files (skipping empties) and persists their metadata. */
    private void storeFiles(Long skillId, MultipartFile[] files) {
        storeFiles(skillId, files, "N");
    }

    /** Stores files with the given pending flag ('Y' = staged version update). */
    private void storeFiles(Long skillId, MultipartFile[] files, String pendingYn) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String relativePath = skillFileStorageService.store(skillId, file);
            skillFileRepository.save(SkillFile.builder()
                    .skillId(skillId)
                    .fileName(file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                            ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                            : file.getOriginalFilename())
                    .filePath(relativePath)
                    .fileSize(file.getSize())
                    .pendingYn(pendingYn)
                    .build());
        }
    }

    /** Maps skill files to detail-popup items. */
    private List<SkillFileItem> fileItems(List<SkillFile> files) {
        return files.stream()
                .map(f -> new SkillFileItem(f.getId(), f.getFileName(), f.getFileSize(), f.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Categories & process stages
    // ------------------------------------------------------------------

    /**
     * Validates the given category ids against the master table.
     *
     * @param required when true, at least one category is mandatory
     * @return the deduplicated valid ids
     */
    private List<Long> validateCategoryIds(List<Long> categoryIds, boolean required) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            if (required) {
                throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_REQUIRED,
                        "At least one category is required.");
            }
            return List.of();
        }
        List<Long> deduped = categoryIds.stream().distinct().toList();
        List<SkillCategory> found = skillCategoryRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_CATEGORY_ID,
                    "One or more categories do not exist.");
        }
        return deduped;
    }

    /**
     * Validates the given stage ids against the master table.
     *
     * @param required when true, at least one stage is mandatory
     * @return the deduplicated valid ids
     */
    private List<Long> validateStageIds(List<Long> stageIds, boolean required) {
        if (stageIds == null || stageIds.isEmpty()) {
            if (required) {
                throw new BusinessException(BusinessErrorCode.SKILL_STAGE_REQUIRED,
                        "At least one process stage is required.");
            }
            return List.of();
        }
        List<Long> deduped = stageIds.stream().distinct().toList();
        List<ProcessStage> found = processStageRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_STAGE_ID,
                    "One or more process stages do not exist.");
        }
        return deduped;
    }

    /** Replaces the skill's category mappings with the given ids. */
    private void storeCategoryMappings(Long skillId, List<Long> categoryIds) {
        skillCategoryMappingRepository.deleteBySkillId(skillId);
        for (Long categoryId : categoryIds) {
            skillCategoryMappingRepository.save(SkillCategoryMapping.builder()
                    .skillId(skillId).categoryId(categoryId).build());
        }
    }

    /** Replaces the skill's stage mappings with the given ids. */
    private void storeStageMappings(Long skillId, List<Long> stageIds) {
        skillStageMappingRepository.deleteBySkillId(skillId);
        for (Long stageId : stageIds) {
            skillStageMappingRepository.save(SkillStageMapping.builder()
                    .skillId(skillId).stageId(stageId).build());
        }
    }

    /** Category names per skill id (all requested ids). */
    private Map<Long, List<String>> loadCategoryNames(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SkillCategory> byId = skillCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        return SkillCategoryMappingRepository.groupBySkill(
                        skillCategoryMappingRepository.findBySkillIdIn(ids)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(byId::get)
                                .filter(java.util.Objects::nonNull)
                                .map(SkillCategory::getName)
                                .toList()));
    }

    /** Stage names per skill id, in pipeline order (all requested ids). */
    private Map<Long, List<String>> loadStageNames(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProcessStage> byId = processStageRepository.findAll().stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        Map<Long, List<Long>> grouped = SkillStageMappingRepository.groupBySkill(
                skillStageMappingRepository.findBySkillIdIn(ids));
        Map<Long, List<String>> result = new java.util.HashMap<>();
        grouped.forEach((skillId, stageIds) -> result.put(skillId,
                stageIds.stream()
                        .map(byId::get)
                        .filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(ProcessStage::getSortOrder))
                        .map(ProcessStage::getName)
                        .toList()));
        return result;
    }

    /** Category names of one skill. */
    private List<String> categoryNamesOf(Long skillId) {
        Map<Long, SkillCategory> byId = skillCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        return skillCategoryMappingRepository.findBySkillId(skillId).stream()
                .map(m -> byId.get(m.getCategoryId()))
                .filter(java.util.Objects::nonNull)
                .map(SkillCategory::getName)
                .toList();
    }

    /** Stage names of one skill, in pipeline order. */
    private List<String> stageNamesOf(Long skillId) {
        Map<Long, ProcessStage> byId = processStageRepository.findAll().stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        return skillStageMappingRepository.findBySkillId(skillId).stream()
                .map(m -> byId.get(m.getStageId()))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(ProcessStage::getSortOrder))
                .map(ProcessStage::getName)
                .toList();
    }

    // ------------------------------------------------------------------
    // Search (grid, visibility-aware)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillSearchResult search(SkillSearchRequest request, User viewer) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        Sort.Direction direction = "ASC".equalsIgnoreCase(request.sortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = switch (request.sortBy() == null ? "" : request.sortBy().toLowerCase(Locale.ROOT)) {
            case "name" -> "name";
            default -> "createdAt";
        };
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(direction, sortBy));

        ViewerOrg viewerOrg = viewerOrg(viewer);
        Specification<Skill> spec = Specification.allOf(
                SkillRepository.visibleTo(viewer, viewerOrg.parts(), viewerOrg.groups()),
                searchCriteria(request));

        Page<Skill> result = skillRepository.findAll(spec, pageable);
        List<Skill> skills = result.getContent();

        Map<Long, User> creators = loadCreators(skills);
        Map<Long, RatingAgg> ratings = loadRatings(skills);
        Set<Long> subscribed = loadSubscribed(skills, viewer.getId());
        Map<Long, List<String>> categoriesBySkill = loadCategoryNames(skills);
        Map<Long, List<String>> stagesBySkill = loadStageNames(skills);

        List<SkillListItem> items = skills.stream().map(s -> {
            RatingAgg agg = ratings.get(s.getId());
            return new SkillListItem(
                    s.getId(), s.getName(), s.getDescription(),
                    s.getCreatedBy(),
                    creators.get(s.getCreatedBy()) != null ? creators.get(s.getCreatedBy()).getName() : null,
                    s.getCreatedAt(), s.getApprovalStatus(), s.getPublicScope(),
                    categoriesBySkill.getOrDefault(s.getId(), List.of()),
                    stagesBySkill.getOrDefault(s.getId(), List.of()),
                    s.getViewCount(),
                    agg != null ? agg.average() : null,
                    agg != null ? agg.count() : 0L,
                    subscribed.contains(s.getId()),
                    s.isVersionUpdatePending());
        }).toList();

        return new SkillSearchResult(result.getTotalElements(), page, pageSize, items);
    }

    /** Search criteria (AND between fields, OR within a multi-value field). */
    private Specification<Skill> searchCriteria(SkillSearchRequest r) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (notBlank(r.skillName())) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + r.skillName().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (notBlank(r.description())) {
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + r.description().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (r.tags() != null && !r.tags().isEmpty()) {
                List<String> lowerTags = r.tags().stream()
                        .map(t -> t.trim().toLowerCase(Locale.ROOT))
                        .filter(t -> !t.isEmpty())
                        .toList();
                if (!lowerTags.isEmpty()) {
                    predicates.add(hashtagContains(root, cb, lowerTags));
                }
            }
            if (r.creatorId() != null) {
                predicates.add(cb.equal(root.get("createdBy"), r.creatorId()));
            }
            if (r.createdDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        r.createdDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (r.createdDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        r.createdDateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (r.statuses() != null && !r.statuses().isEmpty()) {
                predicates.add(root.get("approvalStatus").in(r.statuses()));
            }
            if (r.scopes() != null && !r.scopes().isEmpty()) {
                predicates.add(root.get("publicScope").in(r.scopes()));
            }
            if (r.categoryIds() != null && !r.categoryIds().isEmpty()) {
                predicates.add(hasCategory(root, query, cb, r.categoryIds()));
            }
            if (r.stageId() != null) {
                predicates.add(hasStage(root, query, cb, r.stageId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** EXISTS a mapping of the skill to any of the given categories. */
    private Predicate hasCategory(Root<Skill> root, CriteriaQuery<?> query,
                                  CriteriaBuilder cb, List<Long> categoryIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<SkillCategoryMapping> m = sq.from(SkillCategoryMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("skillId"), root.get("id")),
                m.get("categoryId").in(categoryIds));
        return cb.exists(sq);
    }

    /** EXISTS a mapping of the skill to the given process stage. */
    private Predicate hasStage(Root<Skill> root, CriteriaQuery<?> query,
                               CriteriaBuilder cb, Long stageId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<SkillStageMapping> m = sq.from(SkillStageMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("skillId"), root.get("id")),
                cb.equal(m.get("stageId"), stageId));
        return cb.exists(sq);
    }

    /**
     * Matches any of the given tags inside the JSONB array.
     *
     * <p>Implemented as {@code lower(text(hashtags)) LIKE '%"tag"%'}
     * (OR across tags). PostgreSQL forbids set-returning functions such as
     * {@code jsonb_array_elements_text} in a WHERE clause, and this Hibernate
     * version does not render {@code Path.as(String.class)} as a CAST in SQL,
     * so the jsonb→text conversion is written as the {@code text()} function
     * (verified valid on PostgreSQL). The quoted-element pattern is exact:
     * tags are validated as {@code [A-Za-z0-9_-]}, so they cannot contain
     * quote or LIKE-wildcard characters.
     */
    private Predicate hashtagContains(Root<Skill> root, CriteriaBuilder cb, List<String> lowerTags) {
        Expression<String> asText = cb.lower(
                cb.function("text", String.class, root.get("hashtags")));
        Predicate[] likes = lowerTags.stream()
                .map(t -> cb.like(asText, "%\"" + t + "\"%"))
                .toArray(Predicate[]::new);
        return cb.or(likes);
    }

    // ------------------------------------------------------------------
    // Detail (Detail SRS §4, TC-38: not visible -> 404)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillDetailResponse getDetail(Long skillId, User viewer) {
        Skill skill = skillAccessHelper.getVisible(skillId, viewer);

        boolean isAdmin = viewer.getRole() != null
                && "ADMIN_USER".equals(viewer.getRole().getName());
        boolean isCreator = skill.getCreatedBy().equals(viewer.getId());
        boolean canEdit = (isCreator || isAdmin)
                && !"Y".equals(skill.getDeletedYn())
                && !Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus());

        BigDecimal avg = skillRatingRepository.averageRatingBySkillId(skillId);
        long ratingCount = skillRatingRepository.countBySkillId(skillId);
        User creator = userRepository.findById(skill.getCreatedBy()).orElse(null);
        boolean subscribed = skillSubscriptionRepository.existsBySkillIdAndUserId(skillId, viewer.getId());
        List<SkillFileItem> files = fileItems(skillFileRepository.findBySkillIdAndPendingYn(skillId, "N"));

        // The staged version (under review) — shown to creator/admin so they
        // can compare it with the live version in the main content.
        SkillPendingVersionItem pendingVersion = skill.getPendingMetadata() != null
                ? buildPendingVersion(skill) : null;

        return new SkillDetailResponse(
                skill.getId(), skill.getName(), skill.getDescription(),
                skill.getApprovalStatus(), skill.getPublicScope(), skill.getRejectionReason(),
                skill.getHashtags() != null ? skill.getHashtags() : List.of(),
                files, skill.getViewCount(),
                skill.getVersion(), skill.getCreatedBy(),
                creator != null ? creator.getName() : null,
                skill.getCreatedAt(), skill.getUpdatedAt(),
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP).doubleValue() : null,
                ratingCount, isCreator, isAdmin, canEdit, subscribed,
                skill.getPendingMetadata() != null, pendingVersion,
                categoryNamesOf(skillId), stageNamesOf(skillId));
    }

    /** Builds the staged-version payload for review (creator/admin). */
    private SkillPendingVersionItem buildPendingVersion(Skill skill) {
        SkillPendingMetadata pending = skill.getPendingMetadata();
        List<SkillFileItem> newFiles = fileItems(
                skillFileRepository.findBySkillIdAndPendingYn(skill.getId(), "Y"));
        List<Long> removeIds = skill.getPendingRemoveFileIds() != null
                ? skill.getPendingRemoveFileIds() : List.of();
        List<SkillFileItem> removedFiles = removeIds.isEmpty()
                ? List.of()
                : fileItems(skillFileRepository.findAllById(removeIds).stream()
                        .filter(f -> f.getSkillId().equals(skill.getId()))
                        .toList());
        Map<Long, SkillCategory> categoriesById = skillCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        Map<Long, ProcessStage> stagesById = processStageRepository.findAll().stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        List<String> pendingCategories = (pending.categoryIds() != null ? pending.categoryIds() : List.<Long>of())
                .stream().map(categoriesById::get).filter(java.util.Objects::nonNull)
                .map(SkillCategory::getName).toList();
        List<String> pendingStages = (pending.stageIds() != null ? pending.stageIds() : List.<Long>of())
                .stream().map(stagesById::get).filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(ProcessStage::getSortOrder))
                .map(ProcessStage::getName).toList();
        return new SkillPendingVersionItem(
                pending.name(), pending.description(), pending.publicScope(),
                pending.hashtags() != null ? pending.hashtags() : List.of(),
                pendingCategories, pendingStages, newFiles, removedFiles);
    }

    // ------------------------------------------------------------------
    // Bulk delete (all-or-nothing)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SkillDeleteCheckResponse checkDelete(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) {
            return new SkillDeleteCheckResponse(true, List.of());
        }
        List<Skill> skills = skillRepository.findByIdIn(ids);
        Map<Long, User> creators = loadCreators(skills);
        boolean isAdmin = isAdmin(user);
        List<SkillDeleteCheckResponse.DeniedSkill> denied = skills.stream()
                .filter(s -> !isAdmin && !s.getCreatedBy().equals(user.getId()))
                .map(s -> new SkillDeleteCheckResponse.DeniedSkill(
                        s.getId(), s.getName(),
                        s.getCreatedBy() != null && creators.get(s.getCreatedBy()) != null
                                ? creators.get(s.getCreatedBy()).getName() : null))
                .toList();
        return new SkillDeleteCheckResponse(denied.isEmpty(), denied);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Skill> skills = skillRepository.findByIdIn(ids);
        Map<Long, User> creators = loadCreators(skills);
        boolean isAdmin = isAdmin(user);
        List<SkillDeleteCheckResponse.DeniedSkill> denied = skills.stream()
                .filter(s -> !isAdmin && !s.getCreatedBy().equals(user.getId()))
                .map(s -> new SkillDeleteCheckResponse.DeniedSkill(
                        s.getId(), s.getName(),
                        creators.get(s.getCreatedBy()) != null
                                ? creators.get(s.getCreatedBy()).getName() : null))
                .toList();
        if (!denied.isEmpty()) {
            // All-or-nothing: nothing is deleted.
            throw new BusinessException(BusinessErrorCode.SKILL_DELETE_FORBIDDEN,
                    "You do not have permission to delete: "
                            + denied.stream().map(SkillDeleteCheckResponse.DeniedSkill::name).toList());
        }
        for (Skill skill : skills) {
            skill.setDeletedYn("Y");
            skillRepository.save(skill);
            for (SkillFile sf : skillFileRepository.findBySkillId(skill.getId())) {
                skillFileStorageService.delete(sf.getFilePath());
            }
            skillFileRepository.deleteBySkillId(skill.getId());
            skillSubscriptionRepository.deleteBySkillId(skill.getId());
        }
    }

    // ------------------------------------------------------------------
    // View count (idempotent: 1 user / skill / view-window)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int incrementView(Long skillId, User viewer) {
        Skill skill = skillAccessHelper.getVisible(skillId, viewer);

        Instant now = Instant.now();
        Optional<SkillViewLog> last =
                skillViewLogRepository.findTopBySkillIdAndUserIdOrderByViewedAtDesc(skillId, viewer.getId());
        if (last.isEmpty()
                || ChronoUnit.MINUTES.between(last.get().getViewedAt(), now) >= viewWindowMinutes) {
            skill.setViewCount(skill.getViewCount() + 1);
            skillRepository.save(skill);
            skillViewLogRepository.save(SkillViewLog.builder()
                    .skillId(skillId)
                    .userId(viewer.getId())
                    .viewedAt(now)
                    .build());
        }
        return skill.getViewCount();
    }

    // ------------------------------------------------------------------
    // Export Excel (in-memory workbook — NASC-safe)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(SkillSearchRequest request, List<Long> selectedIds, User viewer) {
        ViewerOrg viewerOrg = viewerOrg(viewer);
        Specification<Skill> spec = Specification.allOf(
                SkillRepository.visibleTo(viewer, viewerOrg.parts(), viewerOrg.groups()),
                searchCriteria(request));
        if (selectedIds != null && !selectedIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("id").in(selectedIds));
        }
        List<Skill> skills = skillRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Map<Long, User> creators = loadCreators(skills);
        Map<Long, RatingAgg> ratings = loadRatings(skills);
        Map<Long, List<SkillFile>> filesBySkill = skillFileRepository
                .findBySkillIdIn(skills.stream().map(Skill::getId).toList()).stream()
                .filter(f -> "N".equals(f.getPendingYn()))
                .collect(Collectors.groupingBy(SkillFile::getSkillId));

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Skills");
            ExcelExportStyle.freezeHeader(sheet);
            CellStyle headerStyle = ExcelExportStyle.headerStyle(workbook);
            CellStyle dataStyle = ExcelExportStyle.dataStyle(workbook);
            CellStyle wrapStyle = ExcelExportStyle.wrapStyle(workbook);

            String[] columns = {"Skill Name", "Description", "Creator", "Created Date",
                    "Status", "Views", "Rating", "Files"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                ExcelExportStyle.setStyled(header, i, columns[i], headerStyle);
            }
            int rowIdx = 1;
            for (Skill s : skills) {
                Row row = sheet.createRow(rowIdx++);
                ExcelExportStyle.setStyled(row, 0, s.getName(), dataStyle);
                ExcelExportStyle.setStyled(row, 1,
                        s.getDescription() != null ? s.getDescription() : "", wrapStyle);
                ExcelExportStyle.setStyled(row, 2, creators.get(s.getCreatedBy()) != null
                        ? creators.get(s.getCreatedBy()).getName() : "", dataStyle);
                ExcelExportStyle.setStyled(row, 3, s.getCreatedAt() != null
                        ? s.getCreatedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "", dataStyle);
                ExcelExportStyle.setStyled(row, 4, s.getApprovalStatus(), dataStyle);
                ExcelExportStyle.setStyled(row, 5, s.getViewCount(), dataStyle);
                RatingAgg agg = ratings.get(s.getId());
                ExcelExportStyle.setStyled(row, 6, agg != null
                        ? String.format("★ %.1f (%d)", agg.average(), agg.count())
                        : "No rating", dataStyle);
                ExcelExportStyle.setStyled(row, 7, filesBySkill.getOrDefault(s.getId(), List.of()).stream()
                        .map(SkillFile::getFileName)
                        .collect(Collectors.joining(", ")), dataStyle);
            }
            // Column widths: fit content (min 12 chars), Description capped at 60 chars.
            ExcelExportStyle.autoSizeColumns(sheet, columns.length - 1, 60);
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Không thể tạo file export skill", e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to export the skill list.");
        }
    }

    // ------------------------------------------------------------------
    // Edit (optimistic lock + FIELD_READ_ONLY)
    // ------------------------------------------------------------------

    private static final int EDIT_MAX_PER_MINUTE = 10;
    private static final int EDIT_WINDOW_SECONDS = 60;

    @Override
    @Transactional
    public int update(Long skillId, String name, String description, String publicScope,
                      String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                      MultipartFile[] files,
                      Instant expectedUpdatedAt, User user) {
        if (!rateLimiter.allow("skill-edit:" + user.getId(),
                EDIT_MAX_PER_MINUTE, EDIT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = skill.getCreatedBy().equals(user.getId());

        if (!isAdmin && !isCreator) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_EDITABLE,
                    "You do not have permission to edit this skill.");
        }
        if (Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_EDITABLE,
                    "A skill pending approval cannot be edited.");
        }
        if (expectedUpdatedAt != null && !expectedUpdatedAt.equals(skill.getUpdatedAt())) {
            throw new BusinessException(BusinessErrorCode.SKILL_OPTIMISTIC_LOCK,
                    "Skill has been changed by another user.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_REQUIRED,
                    "Skill name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_TOO_LONG,
                    "Skill name is at most 100 characters.");
        }
        boolean nameChanged = !trimmedName.equals(skill.getName());
        // PUBLISHED + non-admin: name is read-only.
        if (nameChanged && !isAdmin && Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
            throw new BusinessException(BusinessErrorCode.SKILL_FIELD_READ_ONLY,
                    "The skill name cannot be changed for a published skill.");
        }
        if (nameChanged && skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(
                trimmedName, skillId, "N", skill.getCreatedBy())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_DUPLICATE,
                    "You already have a skill with this name.");
        }
        if (publicScope == null || !ALLOWED_SCOPES.contains(publicScope)) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_SCOPE,
                    "Invalid public scope.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);

        boolean wasPublished = Skill.STATUS_APPROVED.equals(skill.getApprovalStatus());

        skill.setName(trimmedName);
        skill.setDescription(description == null || description.isBlank() ? null : description);
        skill.setPublicScope(publicScope);
        skill.setHashtags(hashtags);

        // Files are only managed through the version-update flow for approved
        // skills; for DRAFT/REJECTED the upload still appends live files.
        if (!wasPublished) {
            storeFiles(skillId, files);
        }
        storeCategoryMappings(skillId, categories);
        storeStageMappings(skillId, stages);

        // Version number only advances when a version update is approved.
        skill.setUpdatedAt(Instant.now());
        skillRepository.save(skill);

        if (wasPublished) {
            notifySubscribers(skill);
        }
        return skill.getVersion();
    }

    /** In-app + email notification to all subscribers of a newly-versioned skill. */
    private void notifySubscribers(Skill skill) {
        List<SkillSubscription> subs =
                skillSubscriptionRepository.findBySkillId(skill.getId());
        if (subs.isEmpty()) {
            return;
        }
        List<User> subscribers = userRepository.findAllById(
                subs.stream().map(s -> s.getUserId()).toList());
        String title = "Skill \"" + skill.getName() + "\" has a new version.";
        String body = "The skill \"" + skill.getName() + "\" you are subscribed to has a new version.";
        for (User sub : subscribers) {
            UserSettings settings =
                    userSettingsRepository.findById(sub.getId())
                            .orElse(UserSettings.builder()
                                    .userId(sub.getId()).inappNotify(true).emailNotify(true).build());
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
                    log.warn("Không thể gửi email thông báo version mới cho {}: {}", sub.getEmail(), e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public void deleteFile(Long skillId, Long fileId, User user) {
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = skill.getCreatedBy().equals(user.getId());
        if (!isAdmin && !isCreator) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_EDITABLE,
                    "You do not have permission to edit this skill.");
        }
        if (Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_LOCKED,
                    "Files of an approved skill can only be managed via Update Version.");
        }
        SkillFile file = skillFileRepository.findById(fileId)
                .filter(f -> f.getSkillId().equals(skillId))
                .filter(f -> "N".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                        "Skill file does not exist."));
        skillFileStorageService.delete(file.getFilePath());
        skillFileRepository.delete(file);
        skill.setUpdatedAt(Instant.now());
        skillRepository.save(skill);
    }

    // ------------------------------------------------------------------
    // Version update (APPROVED skills — staged, review before going live)
    // ------------------------------------------------------------------

    private static final int VERSION_MAX_PER_MINUTE = 5;
    private static final int VERSION_WINDOW_SECONDS = 60;

    @Override
    @Transactional
    public void updateVersion(Long skillId, String name, String description, String publicScope,
                              String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                              MultipartFile[] files, List<Long> removeFileIds, User user) {
        if (!rateLimiter.allow("skill-version:" + user.getId(),
                VERSION_MAX_PER_MINUTE, VERSION_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = skill.getCreatedBy().equals(user.getId());
        if (!isAdmin && !isCreator) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_EDITABLE,
                    "You do not have permission to update this skill.");
        }
        if (!Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
            throw new BusinessException(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED,
                    "Only approved skills can receive a version update.");
        }
        if (skill.getPendingMetadata() != null) {
            throw new BusinessException(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED,
                    "A version update is already pending approval.");
        }
        long newFileCount = files == null ? 0
                : java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).count();
        if (newFileCount == 0) {
            throw new BusinessException(BusinessErrorCode.SKILL_VERSION_FILE_REQUIRED,
                    "At least one new file is required to update the version.");
        }

        // Same metadata rules as a regular edit (name IS changeable here —
        // it goes through review).
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_REQUIRED,
                    "Skill name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_TOO_LONG,
                    "Skill name is at most 100 characters.");
        }
        if (!trimmedName.equals(skill.getName()) && skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(
                trimmedName, skillId, "N", skill.getCreatedBy())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NAME_DUPLICATE,
                    "You already have a skill with this name.");
        }
        if (publicScope == null || !ALLOWED_SCOPES.contains(publicScope)) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_SCOPE,
                    "Invalid public scope.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);

        // Files to remove must be live files of this skill.
        List<Long> removeIds = removeFileIds == null ? List.of() : removeFileIds.stream().distinct().toList();
        if (!removeIds.isEmpty()) {
            List<SkillFile> removable = skillFileRepository.findAllById(removeIds).stream()
                    .filter(f -> f.getSkillId().equals(skillId))
                    .filter(f -> "N".equals(f.getPendingYn()))
                    .toList();
            if (removable.size() != removeIds.size()) {
                throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                        "One or more files to remove do not exist.");
            }
        }

        // Stage the update: pending files + staged metadata, then PENDING_APPROVAL.
        storeFiles(skillId, files, "Y");
        skill.setPendingMetadata(new SkillPendingMetadata(
                trimmedName,
                description == null || description.isBlank() ? null : description,
                publicScope, hashtags, categories, stages));
        skill.setPendingRemoveFileIds(removeIds);
        skillRepository.save(skill);

        skillWorkflowService.transition(skillId, user, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Override
    @Transactional
    public void cancelVersionUpdate(Long skillId, User user) {
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = skill.getCreatedBy().equals(user.getId());
        if (!isAdmin && !isCreator) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_EDITABLE,
                    "You do not have permission to cancel this version update.");
        }
        if (!Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus())
                || skill.getPendingMetadata() == null) {
            throw new BusinessException(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED,
                    "There is no pending version update to cancel.");
        }
        skillVersionUpdateService.discardPendingVersion(skill);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skillRepository.save(skill);
        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(skillId)
                .fromStatus(Skill.STATUS_PENDING_APPROVAL)
                .toStatus(Skill.STATUS_APPROVED)
                .changedBy(user.getId())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillVersionItem> listVersions(Long skillId, User viewer) {
        Skill skill = skillAccessHelper.getVisible(skillId, viewer);
        Map<Long, User> creators = loadCreators(List.of(skill));
        return skillVersionRepository.findBySkillIdOrderByVersionDesc(skillId).stream()
                .map(v -> new SkillVersionItem(
                        v.getVersion(), v.getName(), v.getDescription(), v.getPublicScope(),
                        v.getHashtags() != null ? v.getHashtags() : List.of(),
                        creators.get(v.getCreatedBy()) != null ? creators.get(v.getCreatedBy()).getName() : null,
                        v.getCreatedAt(),
                        skillVersionFileRepository.findBySkillVersionId(v.getId()).stream()
                                .map(f -> new SkillVersionFileItem(f.getId(), f.getFileName(), f.getFileSize()))
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SkillVersionFileDownload downloadVersionFile(Long skillId, int version, Long fileId, User viewer) {
        skillAccessHelper.getVisible(skillId, viewer);
        SkillVersion sv = findVersion(skillId, version);
        SkillVersionFile vf = skillVersionFileRepository.findById(fileId)
                .filter(f -> f.getSkillVersionId().equals(sv.getId()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_VERSION_FILE_NOT_FOUND,
                        "Version file does not exist."));
        return new SkillVersionFileDownload(vf.getFileName(), skillFileStorageService.load(vf.getFilePath()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillVersionFile> listVersionFiles(Long skillId, int version, User viewer) {
        skillAccessHelper.getVisible(skillId, viewer);
        SkillVersion sv = findVersion(skillId, version);
        List<SkillVersionFile> files = skillVersionFileRepository.findBySkillVersionId(sv.getId());
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "This version has no files.");
        }
        return files;
    }

    /** Finds one snapshot version of a skill (404 SKILL_VERSION_NOT_FOUND). */
    private SkillVersion findVersion(Long skillId, int version) {
        return skillVersionRepository
                .findBySkillIdOrderByVersionDesc(skillId).stream()
                .filter(v -> v.getVersion() == version)
                .findFirst()
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_VERSION_NOT_FOUND,
                        "Skill version does not exist."));
    }

    // ------------------------------------------------------------------
    // Subscription
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void subscribe(Long skillId, User user) {
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())
                && !skill.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_APPROVED,
                    "Only published skills can be subscribed.");
        }
        if (skill.getCreatedBy().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_SUBSCRIBE_FORBIDDEN,
                    "You cannot subscribe to your own skill.");
        }
        if (!skillSubscriptionRepository.existsBySkillIdAndUserId(skillId, user.getId())) {
            skillSubscriptionRepository.save(
                    SkillSubscription.builder()
                            .skillId(skillId).userId(user.getId()).build());
        }
    }

    @Override
    @Transactional
    public void unsubscribe(Long skillId, User user) {
        skillAccessHelper.getVisible(skillId, user);
        skillSubscriptionRepository.deleteBySkillIdAndUserId(skillId, user.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }

    /** The viewer's distinct Part / Group values (org rows Employed=Y). */
    private ViewerOrg viewerOrg(User viewer) {
        List<OrganizationMaster> rows = organizationRepository
                .findByKnoxIdIgnoreCase(viewer.getKnoxId()).stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .toList();
        Set<String> parts = rows.stream()
                .map(o -> o.getPart().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> groups = rows.stream()
                .map(o -> o.getGroupName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return new ViewerOrg(parts, groups);
    }

    private record ViewerOrg(Set<String> parts, Set<String> groups) {
    }

    private record RatingAgg(double average, long count) {
    }

    private Map<Long, User> loadCreators(List<Skill> skills) {
        Set<Long> ids = skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, RatingAgg> loadRatings(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SkillRating> ratings = skillRatingRepository.findBySkillIdIn(ids);
        return ratings.stream().collect(Collectors.groupingBy(
                SkillRating::getSkillId,
                Collectors.reducing(
                        new RatingAgg(0, 0),
                        r -> new RatingAgg(r.getRating().doubleValue(), 1),
                        (a, b) -> new RatingAgg(a.average() + b.average(), a.count() + b.count()))))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new RatingAgg(e.getValue().average() / e.getValue().count(), e.getValue().count())));
    }

    private Set<Long> loadSubscribed(List<Skill> skills, Long viewerId) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return skillSubscriptionRepository.findByUserId(viewerId).stream()
                .map(s -> s.getSkillId())
                .filter(ids::contains)
                .collect(Collectors.toSet());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
