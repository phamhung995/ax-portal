package com.furelise.skillmanagement.skill.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.skill.dto.*;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.mcp.model.SkillMcpDependency;
import com.furelise.skillmanagement.mcp.service.McpDependencyService;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.model.SubSystemMaster;
import com.furelise.skillmanagement.org.model.SystemMaster;
import com.furelise.skillmanagement.skill.model.ProcessStage;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillCategory;
import com.furelise.skillmanagement.skill.model.SkillCategoryMapping;
import com.furelise.skillmanagement.skill.model.SkillCoworkUser;
import com.furelise.skillmanagement.skill.model.SkillDownloadLog;
import com.furelise.skillmanagement.skill.model.SkillFile;
import com.furelise.skillmanagement.skill.model.SkillShare;
import com.furelise.skillmanagement.skill.model.SkillRating;
import com.furelise.skillmanagement.skill.model.SkillStageMapping;
import com.furelise.skillmanagement.skill.model.SkillStatusHistory;
import com.furelise.skillmanagement.skill.model.SkillSubscription;
import com.furelise.skillmanagement.skill.model.SkillVersion;
import com.furelise.skillmanagement.skill.model.SkillVersionFile;
import com.furelise.skillmanagement.skill.model.SkillViewLog;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.skill.repository.ProcessStageRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillCoworkUserRepository;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillViewLogRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.skill.service.ExcelExportStyle;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillFileStorageService;
import com.furelise.skillmanagement.skill.service.SkillService;
import com.furelise.skillmanagement.skill.service.SkillVersionUpdateService;
import com.furelise.skillmanagement.skill.service.SkillWorkflowService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            Skill.SCOPE_PRIVATE, Skill.SCOPE_PROJECT, Skill.SCOPE_PART, Skill.SCOPE_GROUP, Skill.SCOPE_PUBLIC);
    private static final int CREATE_MAX_PER_MINUTE = 5;
    private static final int CREATE_WINDOW_SECONDS = 60;

    /** Minutes between two counted views of the same skill by the same user. */
    @org.springframework.beans.factory.annotation.Value("${app.skill.view-window-minutes:15}")
    private int viewWindowMinutes;

    /** Public frontend base URL — appended to skill links in notification emails. */
    @org.springframework.beans.factory.annotation.Value("${app.base-url:}")
    private String appBaseUrl;

    private final SkillRepository skillRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillStatusHistoryRepository skillStatusHistoryRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final SkillViewLogRepository skillViewLogRepository;
    private final SkillDownloadLogRepository skillDownloadLogRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final ProcessStageRepository processStageRepository;
    private final SkillCategoryMappingRepository skillCategoryMappingRepository;
    private final SkillStageMappingRepository skillStageMappingRepository;
    private final SkillCoworkUserRepository skillCoworkUserRepository;
    private final com.furelise.skillmanagement.skill.repository.SkillShareRepository skillShareRepository;
    private final SkillAccessHelper skillAccessHelper;
    private final McpDependencyService mcpDependencyService;
    private final SkillWorkflowService skillWorkflowService;
    private final SkillVersionUpdateService skillVersionUpdateService;
    private final com.furelise.skillmanagement.skill.repository.SkillVersionRepository skillVersionRepository;
    private final com.furelise.skillmanagement.skill.repository.SkillVersionFileRepository skillVersionFileRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final com.furelise.skillmanagement.org.repository.SystemMasterRepository systemMasterRepository;
    private final com.furelise.skillmanagement.org.repository.SubSystemMasterRepository subSystemMasterRepository;
    private final SkillFileStorageService skillFileStorageService;
    private final RateLimiter rateLimiter;
    private final ReputationService reputationService;
    private final ObjectMapper objectMapper;
    private final com.furelise.skillmanagement.user.repository.UserSettingsRepository userSettingsRepository;
    private final com.furelise.skillmanagement.notification.service.EventNotificationService eventNotificationService;
    private final com.furelise.skillmanagement.audit.service.AuditService auditService;

    // ------------------------------------------------------------------
    // Create (Add SRS §4)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(String name, String description, String publicScope,
                       String systemKey, String subSystemCode,
                       String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                       List<Long> coworkUserIds,
                       MultipartFile[] files, User creator) {
        if (!isAdmin(creator) && !rateLimiter.allow("skill-create:" + creator.getId(),
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

        // PRIVATE and PROJECT skills are auto-approved on creation (they are
        // only visible to the creator's own circle, so no admin review is
        // needed). PART/GROUP/PUBLIC still go through DRAFT → submit →
        // approve. Note: MCP publish validation is skipped for the
        // auto-approved scopes (it only runs on submit/approve).
        String initialStatus = (Skill.SCOPE_PRIVATE.equals(publicScope) || Skill.SCOPE_PROJECT.equals(publicScope))
                ? Skill.STATUS_APPROVED : Skill.STATUS_DRAFT;
        Skill skill = Skill.builder()
                .name(trimmedName)
                .description(description == null || description.isBlank() ? null : description)
                .approvalStatus(initialStatus)
                .publicScope(publicScope)
                .systemKey(normalizeMarker(systemKey))
                .subSystemCode(normalizeMarker(subSystemCode))
                .hashtags(hashtags)
                .createdBy(creator.getId())
                .build();
        Skill saved = skillRepository.save(skill);
        storeFiles(saved.getId(), files);
        storeCategoryMappings(saved.getId(), categories);
        storeStageMappings(saved.getId(), stages);
        applyCoworkUsers(saved, coworkUserIds, creator);

        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(saved.getId())
                .fromStatus(null)
                .toStatus(initialStatus)
                .changedBy(creator.getId())
                .build());

        auditService.log(creator, "CREATE", "SKILL", String.valueOf(saved.getId()),
                "name=" + trimmedName + ", scope=" + publicScope);
        return saved.getId();
    }

    /** Null/blank marker values are stored as null. */
    private static String normalizeMarker(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Scopes that are auto-approved on creation / version update. */
    private static boolean isAutoApproveScope(String scope) {
        return Skill.SCOPE_PRIVATE.equals(scope) || Skill.SCOPE_PROJECT.equals(scope);
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
            case "viewcount" -> "viewCount";
            case "avgrating" -> "avgRating";
            case "reputation" -> "reputation";
            default -> "createdAt";
        };

        SkillAccessHelper.OrgVisibility viewerOrg = skillAccessHelper.visibility(viewer);
        Specification<Skill> spec = Specification.allOf(
                SkillRepository.visibleTo(viewer, viewerOrg),
                searchCriteria(request, viewer, true));

        // avgRating and reputation have no entity column — sort in memory
        // (rating average / cached reputation score, nulls last), then page manually.
        List<Skill> skills;
        long total;
        if ("avgRating".equals(sortBy) || "reputation".equals(sortBy)) {
            List<Skill> all = skillRepository.findAll(spec,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            if ("reputation".equals(sortBy)) {
                // Cached reputation_scores (null = not enough ratings yet, sorts last).
                Map<Long, ReputationScore> scores = reputationService.getReputations(
                        "SKILL", all.stream().map(Skill::getId).toList());
                Comparator<Skill> byRep = Comparator.comparingDouble(
                        (Skill s) -> scores.get(s.getId()) != null
                                && scores.get(s.getId()).getScore() != null
                                ? scores.get(s.getId()).getScore().doubleValue() : -1.0).reversed();
                // Same deterministic tie-break as avgRating (see below).
                all.sort(byRep.thenComparing(Skill::getCreatedAt,
                        Comparator.reverseOrder()).thenComparing(Skill::getId));
            } else {
                Map<Long, Double> avgBySkill = new LinkedHashMap<>();
                if (!all.isEmpty()) {
                    for (Object[] row : skillRatingRepository.ratingAggBySkillIdIn(
                            all.stream().map(Skill::getId).toList())) {
                        avgBySkill.put((Long) row[0], ((Number) row[1]).doubleValue());
                    }
                }
                Comparator<Skill> byAvg = Comparator.comparingDouble(
                        (Skill s) -> avgBySkill.getOrDefault(s.getId(), 0.0)).reversed();
                // Tie-break on the PK: many skills share the same rating (no
                // ratings → 0.0) and createdAt, and without a deterministic
                // order the in-memory page boundaries shift between requests
                // (the same skill appears on two different pages).
                all.sort(byAvg.thenComparing(Skill::getCreatedAt,
                        Comparator.reverseOrder()).thenComparing(Skill::getId));
            }
            total = all.size();
            int from = Math.min((page - 1) * pageSize, all.size());
            int to = Math.min(from + pageSize, all.size());
            skills = List.copyOf(all.subList(from, to));
        } else {
            // Secondary sort on the PK: without it, ties on the sort field
            // (e.g. many skills with 0 views) have an undefined order in
            // PostgreSQL, so the same skill can appear on two different
            // pages when the viewer flips back and forth.
            Page<Skill> result = skillRepository.findAll(spec,
                    PageRequest.of(page - 1, pageSize,
                            Sort.by(direction, sortBy).and(Sort.by("id"))));
            total = result.getTotalElements();
            skills = result.getContent();
        }

        Map<Long, User> creators = loadCreators(skills);
        Map<Long, RatingAgg> ratings = loadRatings(skills);
        Map<Long, ReputationScore> reputations = loadReputations(skills);
        Set<Long> subscribed = loadSubscribed(skills, viewer.getId());
        Map<Long, List<String>> categoriesBySkill = loadCategoryNames(skills);
        Map<Long, List<String>> stagesBySkill = loadStageNames(skills);
        Set<Long> coworkSkillIds = skills.isEmpty() ? Set.of()
                : skillCoworkUserRepository.findBySkillIdIn(skills.stream().map(Skill::getId).toList()).stream()
                        .filter(c -> c.getUserId().equals(viewer.getId()))
                        .map(SkillCoworkUser::getSkillId)
                        .collect(Collectors.toSet());
        // 2026-11-07: same rule as the Home chart — "Download All" events only,
        // the skill creator's own downloads excluded.
        Map<Long, Long> creatorBySkill = skills.stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCreatedBy));
        Map<Long, Long> downloadCounts = skills.isEmpty() ? Map.of()
                : skillDownloadLogRepository.findBySkillIdIn(skills.stream().map(Skill::getId).toList()).stream()
                        .filter(SkillDownloadLog::isDownloadAll)
                        .filter(l -> !l.getUserId().equals(creatorBySkill.get(l.getSkillId())))
                        .collect(Collectors.groupingBy(SkillDownloadLog::getSkillId,
                                Collectors.counting()));
        // Shares awaiting admin approval per skill (grid badge "Pending (n)").
        Map<Long, Long> pendingShareCounts = skills.isEmpty() ? Map.of()
                : skillShareRepository.findBySkillIdIn(skills.stream().map(Skill::getId).toList()).stream()
                        .filter(s -> SkillShare.STATUS_PENDING.equals(s.getApprovalStatus()))
                        .collect(Collectors.groupingBy(SkillShare::getSkillId, Collectors.counting()));

        List<SkillListItem> items = skills.stream().map(s -> {
            RatingAgg agg = ratings.get(s.getId());
            return new SkillListItem(
                    s.getId(), s.getName(), s.getDescription(),
                    s.getCreatedBy(),
                    creators.get(s.getCreatedBy()) != null ? creators.get(s.getCreatedBy()).getName() : null,
                    s.getCreatedAt(), s.getApprovalStatus(), s.getPublicScope(),
                    s.getSystemKey(), s.getSubSystemCode(),
                    categoriesBySkill.getOrDefault(s.getId(), List.of()),
                    stagesBySkill.getOrDefault(s.getId(), List.of()),
                    s.getViewCount(),
                    agg != null ? agg.average() : null,
                    agg != null ? agg.count() : 0L,
                    subscribed.contains(s.getId()),
                    s.isVersionUpdatePending(),
                    s.getPendingReason(),
                    pendingShareCounts.getOrDefault(s.getId(), 0L).intValue(),
                    coworkSkillIds.contains(s.getId()),
                    downloadCounts.getOrDefault(s.getId(), 0L),
                    reputations.get(s.getId()) != null && reputations.get(s.getId()).getScore() != null
                            ? reputations.get(s.getId()).getScore().doubleValue() : null,
                    reputations.get(s.getId()) != null ? reputations.get(s.getId()).getTier() : "NEW");
        }).toList();

        return new SkillSearchResult(total, page, pageSize, items,
                stageCounts(viewer, viewerOrg, request));
    }

    /**
     * Total skills per process stage under the given criteria (visibility +
     * search filters, no paging) — the Process Map pipeline indicators.
     * The stageId filter itself is EXCLUDED so the pipeline totals stay
     * stable when a stage is selected. A skill mapped to several stages
     * counts once per stage.
     */
    private Map<Long, Long> stageCounts(User viewer, SkillAccessHelper.OrgVisibility viewerOrg,
                                        SkillSearchRequest request) {
        Specification<Skill> spec = Specification.allOf(
                SkillRepository.visibleTo(viewer, viewerOrg),
                searchCriteria(request, viewer, false));
        List<Skill> all = skillRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (all.isEmpty()) {
            return Map.of();
        }
        List<SkillStageMapping> mappings = skillStageMappingRepository.findBySkillIdIn(
                all.stream().map(Skill::getId).toList());
        return mappings.stream()
                .collect(Collectors.groupingBy(SkillStageMapping::getStageId, Collectors.counting()));
    }

    /**
     * Search criteria (AND between fields, OR within a multi-value field).
     *
     * @param includeStageId whether to apply the stageId filter (false for the
     *                       Process Map pipeline totals, which must stay
     *                       stable when a stage is selected)
     */
    private Specification<Skill> searchCriteria(SkillSearchRequest r, User viewer, boolean includeStageId) {
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
                // Filter by the grid's DISPLAY status (see
                // SkillRepository.statusFilter) so the result matches the
                // badge the viewer sees — a non-admin's filter "Approved"
                // also returns other users' skills with a pending version
                // update, which display as Approved.
                predicates.add(SkillRepository.statusFilter(viewer, r.statuses())
                        .toPredicate(root, query, cb));
            }
            if (r.scopes() != null && !r.scopes().isEmpty()) {
                predicates.add(root.get("publicScope").in(r.scopes()));
            }
            if (notBlank(r.systemKey())) {
                predicates.add(cb.equal(root.get("systemKey"), r.systemKey().trim()));
            }
            if (notBlank(r.subSystemCode())) {
                predicates.add(cb.equal(root.get("subSystemCode"), r.subSystemCode().trim()));
            }
            if (r.categoryIds() != null && !r.categoryIds().isEmpty()) {
                predicates.add(hasCategory(root, query, cb, r.categoryIds()));
            }
            if (includeStageId && r.stageId() != null) {
                predicates.add(hasStage(root, query, cb, r.stageId()));
            }
            if ("Y".equalsIgnoreCase(r.subscribed())) {
                predicates.add(hasSubscription(root, query, cb, viewer.getId()));
            } else if ("N".equalsIgnoreCase(r.subscribed())) {
                predicates.add(cb.not(hasSubscription(root, query, cb, viewer.getId())));
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

    /** EXISTS a subscription of the given user to the skill. */
    private Predicate hasSubscription(Root<Skill> root, CriteriaQuery<?> query,
                                      CriteriaBuilder cb, Long userId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<SkillSubscription> s = sq.from(SkillSubscription.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(s.get("skillId"), root.get("id")),
                cb.equal(s.get("userId"), userId));
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
        boolean canEdit = (isCreator || isAdmin || isCowork(skill, viewer))
                && !"Y".equals(skill.getDeletedYn())
                && !Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus());

        BigDecimal avg = skillRatingRepository.averageRatingBySkillId(skillId);
        long ratingCount = skillRatingRepository.countBySkillId(skillId);
        User creator = userRepository.findById(skill.getCreatedBy()).orElse(null);
        boolean subscribed = skillSubscriptionRepository.existsBySkillIdAndUserId(skillId, viewer.getId());
        List<SkillFileItem> files = fileItems(skillFileRepository.findBySkillIdAndPendingYn(skillId, "N"));
        // 2026-11-07: same rule as the Home chart — "Download All" events only,
        // the skill creator's own downloads excluded.
        long downloadCount = skillDownloadLogRepository.findBySkillIdIn(List.of(skillId)).stream()
                .filter(SkillDownloadLog::isDownloadAll)
                .filter(l -> !l.getUserId().equals(skill.getCreatedBy()))
                .count();
        List<CoworkUserItem> coworkUsers = coworkUsersOf(skillId);

        // The staged version (under review) — shown to creator/admin so they
        // can compare it with the live version in the main content.
        SkillPendingVersionItem pendingVersion = skill.getPendingMetadata() != null
                ? buildPendingVersion(skill) : null;

        // Shares awaiting admin approval — shown to the creator/admin so they
        // know exactly WHAT is being approved (the pending share list).
        List<SkillShareItem> pendingShares = Skill.PENDING_REASON_SHARE.equals(skill.getPendingReason())
                ? pendingShareItems(skillId) : List.of();

        // 2026-11-07: resolve system / sub-system display names (the skill
        // stores keys; the detail popup shows names, falling back to the key
        // when the master row is gone).
        String systemName = null;
        if (skill.getSystemKey() != null) {
            SystemMaster sys = systemMasterRepository.findBySystemKey(skill.getSystemKey());
            systemName = sys != null ? sys.getSystemName() : null;
        }
        String subSystemName = null;
        if (skill.getSystemKey() != null && skill.getSubSystemCode() != null) {
            SubSystemMaster sub = subSystemMasterRepository.findBySystemKeyAndSubSystemCode(
                    skill.getSystemKey(), skill.getSubSystemCode());
            subSystemName = sub != null ? sub.getSubSystemName() : null;
        }

        return new SkillDetailResponse(
                skill.getId(), skill.getName(), skill.getDescription(),
                skill.getApprovalStatus(), skill.getPublicScope(),
                skill.getSystemKey(), skill.getSubSystemCode(),
                systemName, subSystemName,
                skill.getRejectionReason(),
                skill.getHashtags() != null ? skill.getHashtags() : List.of(),
                files, skill.getViewCount(), downloadCount,
                skill.getVersion(), skill.getCreatedBy(),
                creator != null ? creator.getName() : null,
                skill.getCreatedAt(), skill.getUpdatedAt(),
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP).doubleValue() : null,
                ratingCount, isCreator, isAdmin, canEdit, subscribed,
                skill.getPendingMetadata() != null,
                "Y".equals(skill.getAdminReviewedYn()),
                skill.getPendingReason(), skill.getPendingScope(),
                pendingShares,
                pendingVersion,
                categoryNamesOf(skillId), stageNamesOf(skillId),
                coworkUsers,
                mcpDependencyService.listForSkill(skillId, skill.getPublicScope(), viewer));
    }

    /** The skill's cowork users (id + name), empty list when none. */
    private List<CoworkUserItem> coworkUsersOf(Long skillId) {
        List<SkillCoworkUser> rows = skillCoworkUserRepository.findBySkillId(skillId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(
                rows.stream().map(SkillCoworkUser::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return rows.stream()
                .map(c -> new CoworkUserItem(c.getUserId(),
                        users.get(c.getUserId()) != null ? users.get(c.getUserId()).getName() : null))
                .toList();
    }

    /**
     * The skill's PENDING shares (what the admin is being asked to approve),
     * with the shared users' names resolved.
     */
    private List<SkillShareItem> pendingShareItems(Long skillId) {
        List<SkillShare> shares = skillShareRepository.findBySkillIdAndApprovalStatus(
                skillId, SkillShare.STATUS_PENDING);
        if (shares.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(
                shares.stream().map(SkillShare::getUserId)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return shares.stream()
                .map(s -> new SkillShareItem(s.getId(), s.getShareType(), s.getUserId(),
                        s.getUserId() == null ? null
                                : users.get(s.getUserId()) != null ? users.get(s.getUserId()).getName() : null,
                        s.getProjectKey(), s.getApprovalStatus()))
                .toList();
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
                pending.systemKey(), pending.subSystemCode(),
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
            auditService.log(user, "DELETE", "SKILL", String.valueOf(skill.getId()),
                    "name=" + skill.getName());
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
    // Download log (chart "total downloads" metric)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void recordDownload(Long skillId, User user, boolean downloadAll) {
        skillDownloadLogRepository.save(SkillDownloadLog.builder()
                .skillId(skillId)
                .userId(user.getId())
                .downloadAll(downloadAll)
                .build());
    }

    // ------------------------------------------------------------------
    // Export Excel (in-memory workbook — NASC-safe)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(SkillSearchRequest request, List<Long> selectedIds, User viewer) {
        SkillAccessHelper.OrgVisibility viewerOrg = skillAccessHelper.visibility(viewer);
        Specification<Skill> spec = Specification.allOf(
                SkillRepository.visibleTo(viewer, viewerOrg),
                searchCriteria(request, viewer, true));
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
                      String systemKey, String subSystemCode,
                      String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                      List<Long> coworkUserIds,
                      MultipartFile[] files, List<Long> removeFileIds,
                      List<McpDependencyService.McpDependencyInput> mcpDependencies,
                      Instant expectedUpdatedAt, User user) {
        if (!isAdmin(user) && !rateLimiter.allow("skill-edit:" + user.getId(),
                EDIT_MAX_PER_MINUTE, EDIT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = skill.getCreatedBy().equals(user.getId());

        if (!canManage(skill, user)) {
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
        skill.setSystemKey(normalizeMarker(systemKey));
        skill.setSubSystemCode(normalizeMarker(subSystemCode));
        skill.setHashtags(hashtags);

        // Scope change: widening from PRIVATE/PROJECT to PART/GROUP/PUBLIC
        // on an APPROVED skill requires admin approval — the new scope is
        // staged (pending_reason='SCOPE', pending_scope=new scope) and the
        // skill moves to PENDING_APPROVAL. The old scope still applies until
        // the admin approves. Narrowing or same-tier changes apply directly.
        // A reviewed skill (admin approved it at least once) applies the
        // widening immediately — no further approval needed (V91).
        boolean scopeWidening = wasPublished
                && !publicScope.equals(skill.getPublicScope())
                && isAutoApproveScope(skill.getPublicScope())
                && !isAutoApproveScope(publicScope)
                && !"Y".equals(skill.getAdminReviewedYn());
        if (scopeWidening) {
            skill.setPendingReason(Skill.PENDING_REASON_SCOPE);
            skill.setPendingScope(publicScope);
        } else {
            skill.setPublicScope(publicScope);
        }

        // Files are only managed through the version-update flow for approved
        // skills; for DRAFT/REJECTED the upload still appends live files and
        // removeFileIds removes live files (at least one must remain).
        if (!wasPublished) {
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
                long liveCount = skillFileRepository.findBySkillIdAndPendingYn(skillId, "N").size();
                if (liveCount - removeIds.size() + (files == null ? 0 : files.length) < 1) {
                    throw new BusinessException(BusinessErrorCode.SKILL_FILE_REQUIRED,
                            "At least one file must remain.");
                }
                for (SkillFile f : removable) {
                    skillFileStorageService.delete(f.getFilePath());
                    skillFileRepository.delete(f);
                }
            }
            storeFiles(skillId, files);
        }
        storeCategoryMappings(skillId, categories);
        storeStageMappings(skillId, stages);

        // Cowork list: only the creator or an admin may change it. A cowork
        // user submitting an edit keeps the existing list (param ignored).
        if (isAdmin(user) || isCreator) {
            applyCoworkUsers(skill, coworkUserIds, user);
        }

        // Version number only advances when a version update is approved.
        skill.setUpdatedAt(Instant.now());
        skillRepository.save(skill);

        if (scopeWidening) {
            // The staged scope awaits admin review (old scope still applies).
            skillWorkflowService.transition(skillId, user, Skill.STATUS_PENDING_APPROVAL, null);
        }

        // FR-MCP-014: replace the MCP dependencies (null/empty = clear all).
        // Q-18 v1.25 exception uses the skill's CREATOR (not the editor).
        // 2026-11-06: like files, the dependency set of an APPROVED skill is
        // managed through the version-update flow — a regular edit keeps the
        // existing dependencies (param ignored).
        if (!wasPublished) {
            mcpDependencyService.saveDependencies(skillId, skill.getCreatedBy(), publicScope,
                    mcpDependencies, SkillMcpDependency.SOURCE_MANUAL);
        }

        auditService.log(user, "UPDATE", "SKILL", String.valueOf(skillId),
                "name=" + trimmedName + ", scope=" + publicScope
                        + (wasPublished ? (scopeWidening ? ", scope pending approval" : ", status=APPROVED")
                                : ", status=" + skill.getApprovalStatus()));

        if (wasPublished) {
            notifySubscribers(skill, user);
        }
        return skill.getVersion();
    }

    /** In-app + email notification to all subscribers of an edited published skill. */
    private void notifySubscribers(Skill skill, User actor) {
        List<com.furelise.skillmanagement.skill.model.SkillSubscription> subs =
                skillSubscriptionRepository.findBySkillId(skill.getId());
        if (subs.isEmpty()) {
            return;
        }
        Map<Long, User> subscribers = userRepository.findByIdIn(
                        subs.stream().map(s -> s.getUserId()).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        if (subscribers.isEmpty()) {
            return;
        }
        Map<Long, com.furelise.skillmanagement.user.model.UserSettings> settings =
                userSettingsRepository.findAllById(subscribers.keySet()).stream()
                        .collect(Collectors.toMap(
                                com.furelise.skillmanagement.user.model.UserSettings::getUserId,
                                s -> s, (a, b) -> a));
        String title = "Skill \"" + skill.getName() + "\" has been edited information.";
        String skillUrl = skillDetailUrl(skill.getId());
        String body = "The skill \"" + skill.getName() + "\" you are subscribed has been edited information."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        eventNotificationService.notifyBatch(actor, subscribers, settings,
                subId -> new com.furelise.skillmanagement.notification.service
                        .EventNotificationService.EventDetails(
                        title,
                        new com.furelise.skillmanagement.notification.service
                                .EventNotificationService.EventContext(
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

    @Override
    @Transactional
    public void deleteFile(Long skillId, Long fileId, User user) {
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!canManage(skill, user)) {
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
                              String systemKey, String subSystemCode,
                              String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                              MultipartFile[] files, List<Long> removeFileIds,
                              List<McpDependencyService.McpDependencyInput> mcpDependencies,
                              User user) {
        if (!isAdmin(user) && !rateLimiter.allow("skill-version:" + user.getId(),
                VERSION_MAX_PER_MINUTE, VERSION_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!canManage(skill, user)) {
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

        // Stage the update: pending files + staged metadata.
        storeFiles(skillId, files, "Y");
        skill.setPendingMetadata(new SkillPendingMetadata(
                trimmedName,
                description == null || description.isBlank() ? null : description,
                publicScope, hashtags, categories, stages,
                normalizeMarker(systemKey), normalizeMarker(subSystemCode)));
        skill.setPendingRemoveFileIds(removeIds);
        skillRepository.save(skill);

        // FR-MCP-014: apply the new MCP dependencies IMMEDIATELY (not staged).
        // Must run for BOTH the auto-complete path (PRIVATE/PROJECT) and the
        // pending-review path — and BEFORE the workflow transition so the
        // publish validation (McpPublishValidationService, called inside
        // transition) validates the NEW set. On validation failure the whole
        // transaction rolls back.
        mcpDependencyService.saveDependencies(skillId, skill.getCreatedBy(), publicScope,
                mcpDependencies, SkillMcpDependency.SOURCE_MANUAL);

        // PRIVATE and PROJECT skills are auto-approved (they are only
        // visible to the creator's own circle) — a version update on them
        // completes immediately, no admin review. PART/GROUP/PUBLIC still
        // go through the pending flow.
        // V91 anti-cheat: a skill that was already reviewed by an admin
        // (admin_reviewed_yn='Y') may have been narrowed from a wide scope
        // back to PRIVATE/PROJECT to exploit the auto-complete — so its
        // version updates ALWAYS go through admin review.
        if (isAutoApproveScope(publicScope) && !"Y".equals(skill.getAdminReviewedYn())) {
            skillVersionUpdateService.completeVersionUpdate(skill, user);
            skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                    .skillId(skillId)
                    .fromStatus(Skill.STATUS_APPROVED)
                    .toStatus(Skill.STATUS_APPROVED)
                    .changedBy(user.getId())
                    .build());
            return;
        }

        skillWorkflowService.transition(skillId, user, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Override
    @Transactional
    public void cancelVersionUpdate(Long skillId, User user) {
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!canManage(skill, user)) {
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
                        v.getSystemKey(), v.getSubSystemCode(),
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
                    com.furelise.skillmanagement.skill.model.SkillSubscription.builder()
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

    /** True when the user is a cowork user of the skill (not the creator). */
    private boolean isCowork(Skill skill, User user) {
        return !skill.getCreatedBy().equals(user.getId())
                && skillCoworkUserRepository.existsBySkillIdAndUserId(skill.getId(), user.getId());
    }

    /** Creator-like management rights: creator, cowork user or admin. */
    private boolean canManage(Skill skill, User user) {
        return isAdmin(user) || skill.getCreatedBy().equals(user.getId()) || isCowork(skill, user);
    }

    /**
     * Replaces the skill's cowork list (creator/admin only — enforced by the
     * caller). Newly added users are notified (in-app + email).
     */
    private void applyCoworkUsers(Skill skill, List<Long> requested, User actor) {
        List<Long> newIds = requested == null ? List.of()
                : requested.stream().distinct().toList();
        if (newIds.contains(actor.getId())) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_COWORK,
                    "You cannot add yourself as a cowork.");
        }
        if (!newIds.isEmpty()) {
            List<User> users = userRepository.findAllById(newIds);
            if (users.size() != newIds.size()
                    || users.stream().anyMatch(u -> !"ACTIVE".equals(u.getStatus()))) {
                throw new BusinessException(BusinessErrorCode.SKILL_INVALID_COWORK,
                        "One or more cowork users are invalid.");
            }
        }
        Set<Long> oldIds = skillCoworkUserRepository.findBySkillId(skill.getId()).stream()
                .map(SkillCoworkUser::getUserId).collect(Collectors.toSet());
        List<Long> added = newIds.stream().filter(id -> !oldIds.contains(id)).toList();

        skillCoworkUserRepository.deleteBySkillId(skill.getId());
        for (Long id : newIds) {
            skillCoworkUserRepository.save(SkillCoworkUser.builder()
                    .skillId(skill.getId()).userId(id).build());
        }
        if (!added.isEmpty()) {
            notifyCoworkAdded(skill, added, actor);
        }
    }

    /** In-app + email notification to users newly added as cowork. */
    private void notifyCoworkAdded(Skill skill, List<Long> userIds, User actor) {
        List<User> users = userRepository.findAllById(userIds);
        String title = "You were added as a cowork on skill \"" + skill.getName() + "\".";
        String skillUrl = skillDetailUrl(skill.getId());
        String body = "You now have management rights on the skill \"" + skill.getName()
                + "\" (edit, submit and version update — deleting the skill is limited to its creator)."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        for (User u : users) {
            eventNotificationService.notify(actor, u, null,
                    new com.furelise.skillmanagement.notification.service
                            .EventNotificationService.EventDetails(
                            title,
                            new com.furelise.skillmanagement.notification.service
                                    .EventNotificationService.EventContext(
                                    String.valueOf(skill.getId()), null, null, null, null),
                            "[AX Portal] " + title, body));
        }
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

    private Map<Long, ReputationScore> loadReputations(List<Skill> skills) {
        if (skills.isEmpty()) {
            return Map.of();
        }
        return reputationService.getReputations("SKILL",
                skills.stream().map(Skill::getId).toList());
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
