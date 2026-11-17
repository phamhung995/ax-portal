package com.furelise.skillmanagement.mcp.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.*;
import com.furelise.skillmanagement.mcp.model.*;
import com.furelise.skillmanagement.mcp.repository.*;
import com.furelise.skillmanagement.skill.service.ExcelExportStyle;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpFileStorageService;
import com.furelise.skillmanagement.mcp.service.McpService;
import com.furelise.skillmanagement.mcp.service.McpVersionUpdateService;
import com.furelise.skillmanagement.mcp.service.McpWorkflowService;
import com.furelise.skillmanagement.mcp.util.McpCodeGenerator;
import com.furelise.skillmanagement.mcp.util.ServerKeyGenerator;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
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
import org.springframework.beans.factory.annotation.Value;
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
 * Core MCP Management implementation (MCP Management SRS — rebuilt on the
 * Skill model, PUBLIC only). Visibility (status only, no scope) is enforced
 * by {@link McpMasterRepository#visibleTo} on every read path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_HASHTAGS = 10;
    private static final int MAX_HASHTAG_LENGTH = 30;
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int CREATE_MAX_PER_MINUTE = 5;
    private static final int CREATE_WINDOW_SECONDS = 60;
    private static final int EDIT_MAX_PER_MINUTE = 10;
    private static final int EDIT_WINDOW_SECONDS = 60;
    private static final int VERSION_MAX_PER_MINUTE = 5;
    private static final int VERSION_WINDOW_SECONDS = 60;

    /** Minutes between two counted views of the same MCP by the same user. */
    @Value("${app.mcp.view-window-minutes:15}")
    private int viewWindowMinutes;

    /** Public frontend base URL — appended to MCP links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    private final McpMasterRepository mcpMasterRepository;
    private final McpFileRepository mcpFileRepository;
    private final McpRatingRepository mcpRatingRepository;
    private final McpStatusHistoryRepository mcpStatusHistoryRepository;
    private final McpSubscriptionRepository mcpSubscriptionRepository;
    private final McpViewLogRepository mcpViewLogRepository;
    private final McpDownloadLogRepository mcpDownloadLogRepository;
    private final McpCategoryRepository mcpCategoryRepository;
    private final McpStageRepository mcpStageRepository;
    private final McpCategoryMappingRepository mcpCategoryMappingRepository;
    private final McpStageMappingRepository mcpStageMappingRepository;
    private final McpVersionRepository mcpVersionRepository;
    private final McpVersionFileRepository mcpVersionFileRepository;
    private final McpAccessHelper mcpAccessHelper;
    private final McpWorkflowService mcpWorkflowService;
    private final McpVersionUpdateService mcpVersionUpdateService;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final SkillMcpDependencyRepository skillMcpDependencyRepository;
    private final McpFileStorageService mcpFileStorageService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final AuditService auditService;
    private final ReputationService reputationService;

    // ------------------------------------------------------------------
    // Create (Add SRS §4)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(String name, String description, String hashtagsJson,
                       List<Long> categoryIds, List<Long> stageIds,
                       MultipartFile[] files, User creator) {
        if (!isAdmin(creator) && !rateLimiter.allow("mcp-create:" + creator.getId(),
                CREATE_MAX_PER_MINUTE, CREATE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.MCP_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_REQUIRED,
                    "MCP name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_TOO_LONG,
                    "MCP name is at most 200 characters.");
        }
        if (mcpMasterRepository.existsByMcpNameIgnoreCaseAndDeletedYnAndCreatedBy(trimmedName, "N", creator.getId())) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_DUPLICATE,
                    "You already have an MCP with this name.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);
        long fileCount = files == null ? 0
                : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).count();
        if (fileCount == 0) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_REQUIRED,
                    "At least one file is required.");
        }

        // PUBLIC only: every MCP goes through DRAFT → submit → approve.
        // mcp_code + server_key are NOT NULL, so they must be set before the
        // first INSERT (the code needs the max sequence, the slug needs the
        // name; only the empty-slug fallback needs the generated id).
        int maxSeq = mcpMasterRepository.findByDeletedYn("N").stream()
                .map(McpMaster::getMcpCode)
                .filter(c -> c != null && c.startsWith(McpCodeGenerator.PREFIX))
                .mapToInt(c -> {
                    try {
                        return Integer.parseInt(c.substring(McpCodeGenerator.PREFIX.length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max().orElse(0);
        String serverKeySlug = ServerKeyGenerator.slugify(trimmedName);
        McpMaster mcp = McpMaster.builder()
                .mcpName(trimmedName)
                .description(description == null || description.isBlank() ? null : description)
                .status(McpMaster.STATUS_DRAFT)
                .hashtags(hashtags)
                .createdBy(creator.getId())
                .mcpCode(McpCodeGenerator.next(maxSeq))
                // Placeholder when the slug is empty — replaced with the
                // id-based fallback after the INSERT (UUID avoids a
                // concurrent duplicate on the unique server_key index).
                .serverKey(serverKeySlug.isEmpty() ? "mcp-" + java.util.UUID.randomUUID() : serverKeySlug)
                .build();
        McpMaster saved = mcpMasterRepository.save(mcp);
        if (serverKeySlug.isEmpty()) {
            saved.setServerKey(ServerKeyGenerator.generate(trimmedName, saved.getId()));
            mcpMasterRepository.save(saved);
        }

        storeFiles(saved.getId(), files);
        storeCategoryMappings(saved.getId(), categories);
        storeStageMappings(saved.getId(), stages);

        mcpStatusHistoryRepository.save(McpStatusHistory.builder()
                .mcpId(saved.getId())
                .fromStatus(null)
                .toStatus(McpMaster.STATUS_DRAFT)
                .changedBy(creator.getId())
                .build());

        auditService.log(creator, "CREATE", "MCP", String.valueOf(saved.getId()),
                "name=" + trimmedName);
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
            throw new BusinessException(BusinessErrorCode.MCP_INVALID_HASHTAG,
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
                throw new BusinessException(BusinessErrorCode.MCP_HASHTAG_TOO_LONG,
                        "Each hashtag is at most 30 characters.");
            }
            if (!HASHTAG_PATTERN.matcher(trimmed).matches()) {
                throw new BusinessException(BusinessErrorCode.MCP_INVALID_HASHTAG,
                        "Hashtags may only contain letters, numbers, underscore and hyphen.");
            }
            if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue; // case-insensitive dedup
            }
            result.add(trimmed);
        }
        if (result.size() > MAX_HASHTAGS) {
            throw new BusinessException(BusinessErrorCode.MCP_HASHTAGS_TOO_MANY,
                    "At most 10 hashtags are allowed.");
        }
        return result;
    }

    /** Stores the given files (skipping empties) and persists their metadata. */
    private void storeFiles(Long mcpId, MultipartFile[] files) {
        storeFiles(mcpId, files, "N");
    }

    /** Stores files with the given pending flag ('Y' = staged version update). */
    private void storeFiles(Long mcpId, MultipartFile[] files, String pendingYn) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String relativePath = mcpFileStorageService.store(mcpId, file);
            mcpFileRepository.save(McpFile.builder()
                    .mcpId(mcpId)
                    .fileName(file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                            ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                            : file.getOriginalFilename())
                    .filePath(relativePath)
                    .fileSize(file.getSize())
                    .pendingYn(pendingYn)
                    .build());
        }
    }

    /** Maps MCP files to detail-popup items. */
    private List<McpFileItem> fileItems(List<McpFile> files) {
        return files.stream()
                .map(f -> new McpFileItem(f.getId(), f.getFileName(), f.getFileSize(), f.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Categories & stages (MCP's own taxonomy)
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
                throw new BusinessException(BusinessErrorCode.MCP_CATEGORY_REQUIRED,
                        "At least one category is required.");
            }
            return List.of();
        }
        List<Long> deduped = categoryIds.stream().distinct().toList();
        List<McpCategory> found = mcpCategoryRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            throw new BusinessException(BusinessErrorCode.MCP_INVALID_CATEGORY_ID,
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
                throw new BusinessException(BusinessErrorCode.MCP_STAGE_REQUIRED,
                        "At least one process stage is required.");
            }
            return List.of();
        }
        List<Long> deduped = stageIds.stream().distinct().toList();
        List<McpStage> found = mcpStageRepository.findAllById(deduped);
        if (found.size() != deduped.size()) {
            throw new BusinessException(BusinessErrorCode.MCP_INVALID_STAGE_ID,
                    "One or more process stages do not exist.");
        }
        return deduped;
    }

    /** Replaces the MCP's category mappings with the given ids. */
    private void storeCategoryMappings(Long mcpId, List<Long> categoryIds) {
        mcpCategoryMappingRepository.deleteByMcpId(mcpId);
        for (Long categoryId : categoryIds) {
            mcpCategoryMappingRepository.save(McpCategoryMapping.builder()
                    .mcpId(mcpId).categoryId(categoryId).build());
        }
    }

    /** Replaces the MCP's stage mappings with the given ids. */
    private void storeStageMappings(Long mcpId, List<Long> stageIds) {
        mcpStageMappingRepository.deleteByMcpId(mcpId);
        for (Long stageId : stageIds) {
            mcpStageMappingRepository.save(McpStageMapping.builder()
                    .mcpId(mcpId).stageId(stageId).build());
        }
    }

    /** Category names per MCP id (all requested ids). */
    private Map<Long, List<String>> loadCategoryNames(List<McpMaster> mcps) {
        List<Long> ids = mcps.stream().map(McpMaster::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, McpCategory> byId = mcpCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(McpCategory::getId, c -> c));
        return McpCategoryMappingRepository.groupByMcp(
                        mcpCategoryMappingRepository.findByMcpIdIn(ids)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(byId::get)
                                .filter(java.util.Objects::nonNull)
                                .map(McpCategory::getName)
                                .toList()));
    }

    /** Stage names per MCP id, in pipeline order (all requested ids). */
    private Map<Long, List<String>> loadStageNames(List<McpMaster> mcps) {
        List<Long> ids = mcps.stream().map(McpMaster::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, McpStage> byId = mcpStageRepository.findAll().stream()
                .collect(Collectors.toMap(McpStage::getId, s -> s));
        Map<Long, List<Long>> grouped = McpStageMappingRepository.groupByMcp(
                mcpStageMappingRepository.findByMcpIdIn(ids));
        Map<Long, List<String>> result = new java.util.HashMap<>();
        grouped.forEach((mcpId, stageIds) -> result.put(mcpId,
                stageIds.stream()
                        .map(byId::get)
                        .filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(McpStage::getSortOrder))
                        .map(McpStage::getName)
                        .toList()));
        return result;
    }

    /** Category names of one MCP. */
    private List<String> categoryNamesOf(Long mcpId) {
        Map<Long, McpCategory> byId = mcpCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(McpCategory::getId, c -> c));
        return mcpCategoryMappingRepository.findByMcpId(mcpId).stream()
                .map(m -> byId.get(m.getCategoryId()))
                .filter(java.util.Objects::nonNull)
                .map(McpCategory::getName)
                .toList();
    }

    /** Stage names of one MCP, in pipeline order. */
    private List<String> stageNamesOf(Long mcpId) {
        Map<Long, McpStage> byId = mcpStageRepository.findAll().stream()
                .collect(Collectors.toMap(McpStage::getId, s -> s));
        return mcpStageMappingRepository.findByMcpId(mcpId).stream()
                .map(m -> byId.get(m.getStageId()))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(McpStage::getSortOrder))
                .map(McpStage::getName)
                .toList();
    }

    // ------------------------------------------------------------------
    // Search (grid, visibility-aware)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public McpSearchResult search(McpSearchRequest request, User viewer) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        Sort.Direction direction = "ASC".equalsIgnoreCase(request.sortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = switch (request.sortBy() == null ? "" : request.sortBy().toLowerCase(Locale.ROOT)) {
            case "name" -> "mcpName";
            case "viewcount" -> "viewCount";
            case "avgrating" -> "avgRating";
            case "reputation" -> "reputation";
            default -> "createdAt";
        };

        Specification<McpMaster> spec = Specification.allOf(
                McpMasterRepository.visibleTo(viewer),
                searchCriteria(request, viewer, true));

        // avgRating and reputation have no entity column — sort in memory
        // (rating average / cached reputation score, nulls last), then page manually.
        List<McpMaster> mcps;
        long total;
        if ("avgRating".equals(sortBy) || "reputation".equals(sortBy)) {
            List<McpMaster> all = mcpMasterRepository.findAll(spec,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            if ("reputation".equals(sortBy)) {
                // Cached reputation_scores (null = not enough ratings yet, sorts last).
                Map<Long, ReputationScore> scores = reputationService.getReputations(
                        "MCP", all.stream().map(McpMaster::getId).toList());
                Comparator<McpMaster> byRep = Comparator.comparingDouble(
                        (McpMaster m) -> scores.get(m.getId()) != null
                                && scores.get(m.getId()).getScore() != null
                                ? scores.get(m.getId()).getScore().doubleValue() : -1.0).reversed();
                // Same deterministic tie-break as avgRating (see below).
                all.sort(byRep.thenComparing(McpMaster::getCreatedAt,
                        Comparator.reverseOrder()).thenComparing(McpMaster::getId));
            } else {
                Map<Long, Double> avgByMcp = new LinkedHashMap<>();
                if (!all.isEmpty()) {
                    for (Object[] row : mcpRatingRepository.ratingAggByMcpIdIn(
                            all.stream().map(McpMaster::getId).toList())) {
                        avgByMcp.put((Long) row[0], ((Number) row[1]).doubleValue());
                    }
                }
                Comparator<McpMaster> byAvg = Comparator.comparingDouble(
                        (McpMaster m) -> avgByMcp.getOrDefault(m.getId(), 0.0)).reversed();
                // PK tie-break: deterministic page boundaries (same rule as Skill list).
                all.sort(byAvg.thenComparing(McpMaster::getCreatedAt,
                        Comparator.reverseOrder()).thenComparing(McpMaster::getId));
            }
            total = all.size();
            int from = Math.min((page - 1) * pageSize, all.size());
            int to = Math.min(from + pageSize, all.size());
            mcps = List.copyOf(all.subList(from, to));
        } else {
            // PK tie-break: deterministic page boundaries (same rule as Skill list).
            Page<McpMaster> result = mcpMasterRepository.findAll(spec,
                    PageRequest.of(page - 1, pageSize,
                            Sort.by(direction, sortBy).and(Sort.by("id"))));
            total = result.getTotalElements();
            mcps = result.getContent();
        }

        Map<Long, User> creators = loadCreators(mcps);
        Map<Long, RatingAgg> ratings = loadRatings(mcps);
        Map<Long, ReputationScore> reputations = loadReputations(mcps);
        Set<Long> subscribed = loadSubscribed(mcps, viewer.getId());
        Map<Long, List<String>> categoriesByMcp = loadCategoryNames(mcps);
        Map<Long, List<String>> stagesByMcp = loadStageNames(mcps);
        Map<Long, Long> downloadCounts = mcps.isEmpty() ? Map.of()
                : mcpDownloadLogRepository.findByMcpIdIn(mcps.stream().map(McpMaster::getId).toList()).stream()
                        .collect(Collectors.groupingBy(McpDownloadLog::getMcpId,
                                Collectors.counting()));

        List<McpListItem> items = mcps.stream().map(m -> {
            RatingAgg agg = ratings.get(m.getId());
            return new McpListItem(
                    m.getId(), m.getMcpName(), m.getDescription(),
                    m.getMcpCode(), m.getServerKey(),
                    m.getCreatedBy(),
                    creators.get(m.getCreatedBy()) != null ? creators.get(m.getCreatedBy()).getName() : null,
                    m.getCreatedAt(), m.getStatus(),
                    categoriesByMcp.getOrDefault(m.getId(), List.of()),
                    stagesByMcp.getOrDefault(m.getId(), List.of()),
                    m.getViewCount(),
                    agg != null ? agg.average() : null,
                    agg != null ? agg.count() : 0L,
                    subscribed.contains(m.getId()),
                    m.isVersionUpdatePending(),
                    downloadCounts.getOrDefault(m.getId(), 0L),
                    reputations.get(m.getId()) != null && reputations.get(m.getId()).getScore() != null
                            ? reputations.get(m.getId()).getScore().doubleValue() : null,
                    reputations.get(m.getId()) != null ? reputations.get(m.getId()).getTier() : "NEW");
        }).toList();

        return new McpSearchResult(total, page, pageSize, items,
                stageCounts(viewer, request));
    }

    /**
     * Total MCPs per process stage under the given criteria (visibility +
     * search filters, no paging). The stageId filter itself is EXCLUDED so
     * the pipeline totals stay stable when a stage is selected.
     */
    private Map<Long, Long> stageCounts(User viewer, McpSearchRequest request) {
        Specification<McpMaster> spec = Specification.allOf(
                McpMasterRepository.visibleTo(viewer),
                searchCriteria(request, viewer, false));
        List<McpMaster> all = mcpMasterRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (all.isEmpty()) {
            return Map.of();
        }
        List<McpStageMapping> mappings = mcpStageMappingRepository.findByMcpIdIn(
                all.stream().map(McpMaster::getId).toList());
        return mappings.stream()
                .collect(Collectors.groupingBy(McpStageMapping::getStageId, Collectors.counting()));
    }

    /**
     * Search criteria (AND between fields, OR within a multi-value field).
     *
     * @param includeStageId whether to apply the stageId filter (false for
     *                       the pipeline totals)
     */
    private Specification<McpMaster> searchCriteria(McpSearchRequest r, User viewer, boolean includeStageId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (notBlank(r.mcpName())) {
                predicates.add(cb.like(cb.lower(root.get("mcpName")),
                        "%" + r.mcpName().trim().toLowerCase(Locale.ROOT) + "%"));
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
                predicates.add(McpMasterRepository.statusFilter(viewer, r.statuses())
                        .toPredicate(root, query, cb));
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

    /** EXISTS a mapping of the MCP to any of the given categories. */
    private Predicate hasCategory(Root<McpMaster> root, CriteriaQuery<?> query,
                                  CriteriaBuilder cb, List<Long> categoryIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<McpCategoryMapping> m = sq.from(McpCategoryMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("mcpId"), root.get("id")),
                m.get("categoryId").in(categoryIds));
        return cb.exists(sq);
    }

    /** EXISTS a mapping of the MCP to the given process stage. */
    private Predicate hasStage(Root<McpMaster> root, CriteriaQuery<?> query,
                               CriteriaBuilder cb, Long stageId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<McpStageMapping> m = sq.from(McpStageMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("mcpId"), root.get("id")),
                cb.equal(m.get("stageId"), stageId));
        return cb.exists(sq);
    }

    /** EXISTS a subscription of the given user to the MCP. */
    private Predicate hasSubscription(Root<McpMaster> root, CriteriaQuery<?> query,
                                      CriteriaBuilder cb, Long userId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<McpSubscription> s = sq.from(McpSubscription.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(s.get("mcpId"), root.get("id")),
                cb.equal(s.get("userId"), userId));
        return cb.exists(sq);
    }

    /**
     * Matches any of the given tags inside the JSONB array
     * (lower(text(hashtags)) LIKE '%"tag"%' — tags are validated as
     * [A-Za-z0-9_-], so the quoted-element pattern is exact).
     */
    private Predicate hashtagContains(Root<McpMaster> root, CriteriaBuilder cb, List<String> lowerTags) {
        Expression<String> asText = cb.lower(
                cb.function("text", String.class, root.get("hashtags")));
        Predicate[] likes = lowerTags.stream()
                .map(t -> cb.like(asText, "%\"" + t + "\"%"))
                .toArray(Predicate[]::new);
        return cb.or(likes);
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public McpDetailResponse getDetail(Long mcpId, User viewer) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, viewer);

        boolean isAdmin = viewer.getRole() != null
                && "ADMIN_USER".equals(viewer.getRole().getName());
        boolean isCreator = mcp.getCreatedBy().equals(viewer.getId());
        boolean canEdit = (isCreator || isAdmin)
                && !"Y".equals(mcp.getDeletedYn())
                && !McpMaster.STATUS_PENDING_APPROVAL.equals(mcp.getStatus());

        BigDecimal avg = mcpRatingRepository.averageRatingByMcpId(mcpId);
        long ratingCount = mcpRatingRepository.countByMcpId(mcpId);
        User creator = userRepository.findById(mcp.getCreatedBy()).orElse(null);
        boolean subscribed = mcpSubscriptionRepository.existsByMcpIdAndUserId(mcpId, viewer.getId());
        List<McpFileItem> files = fileItems(mcpFileRepository.findByMcpIdAndPendingYn(mcpId, "N"));
        long downloadCount = mcpDownloadLogRepository.findByMcpIdIn(List.of(mcpId)).size();

        // The staged version (under review) — shown to owner/admin so they
        // can compare it with the live version in the main content.
        McpPendingVersionItem pendingVersion = mcp.getPendingMetadata() != null
                ? buildPendingVersion(mcp) : null;

        return new McpDetailResponse(
                mcp.getId(), mcp.getMcpName(), mcp.getDescription(),
                mcp.getMcpCode(), mcp.getServerKey(),
                mcp.getStatus(), mcp.getRejectionReason(),
                mcp.getHashtags() != null ? mcp.getHashtags() : List.of(),
                files, mcp.getViewCount(), downloadCount,
                mcp.getVersion(), mcp.getCreatedBy(),
                creator != null ? creator.getName() : null,
                mcp.getCreatedAt(), mcp.getUpdatedAt(),
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP).doubleValue() : null,
                ratingCount, isCreator, isAdmin, canEdit, subscribed,
                mcp.getPendingMetadata() != null,
                pendingVersion,
                categoryNamesOf(mcpId), stageNamesOf(mcpId),
                dependentSkillsOf(mcpId));
    }

    /** The skills that pin this MCP (reverse reference, "Skills using this MCP" tab). */
    private List<McpDetailResponse.DependentSkillItem> dependentSkillsOf(Long mcpId) {
        List<SkillMcpDependency> deps = skillMcpDependencyRepository.findByMcpId(mcpId);
        if (deps.isEmpty()) {
            return List.of();
        }
        List<Skill> skills = skillRepository.findByIdIn(
                deps.stream().map(SkillMcpDependency::getSkillId).toList());
        Set<Long> creatorIds = skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet());
        Map<Long, User> creators = creatorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(creatorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        return skills.stream()
                .map(s -> new McpDetailResponse.DependentSkillItem(
                        s.getId(), s.getName(), s.getVersion(),
                        creators.get(s.getCreatedBy()) != null
                                ? creators.get(s.getCreatedBy()).getName() : null))
                .toList();
    }

    /** Builds the staged-version payload for review (owner/admin). */
    private McpPendingVersionItem buildPendingVersion(McpMaster mcp) {
        McpPendingMetadata pending = mcp.getPendingMetadata();
        List<McpFileItem> newFiles = fileItems(
                mcpFileRepository.findByMcpIdAndPendingYn(mcp.getId(), "Y"));
        List<Long> removeIds = mcp.getPendingRemoveFileIds() != null
                ? mcp.getPendingRemoveFileIds() : List.of();
        List<McpFileItem> removedFiles = removeIds.isEmpty()
                ? List.of()
                : fileItems(mcpFileRepository.findAllById(removeIds).stream()
                        .filter(f -> f.getMcpId().equals(mcp.getId()))
                        .toList());
        Map<Long, McpCategory> categoriesById = mcpCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(McpCategory::getId, c -> c));
        Map<Long, McpStage> stagesById = mcpStageRepository.findAll().stream()
                .collect(Collectors.toMap(McpStage::getId, s -> s));
        List<String> pendingCategories = (pending.categoryIds() != null ? pending.categoryIds() : List.<Long>of())
                .stream().map(categoriesById::get).filter(java.util.Objects::nonNull)
                .map(McpCategory::getName).toList();
        List<String> pendingStages = (pending.stageIds() != null ? pending.stageIds() : List.<Long>of())
                .stream().map(stagesById::get).filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(McpStage::getSortOrder))
                .map(McpStage::getName).toList();
        return new McpPendingVersionItem(
                pending.name(), pending.description(),
                pending.hashtags() != null ? pending.hashtags() : List.of(),
                pendingCategories, pendingStages, newFiles, removedFiles);
    }

    // ------------------------------------------------------------------
    // Bulk delete (all-or-nothing)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public McpDeleteCheckResponse checkDelete(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) {
            return new McpDeleteCheckResponse(true, List.of());
        }
        List<McpMaster> mcps = mcpMasterRepository.findByIdIn(ids);
        Map<Long, User> creators = loadCreators(mcps);
        boolean isAdmin = isAdmin(user);
        List<McpDeleteCheckResponse.DeniedMcp> denied = mcps.stream()
                .filter(m -> !isAdmin && !m.getCreatedBy().equals(user.getId()))
                .map(m -> new McpDeleteCheckResponse.DeniedMcp(
                        m.getId(), m.getMcpName(),
                        m.getCreatedBy() != null && creators.get(m.getCreatedBy()) != null
                                ? creators.get(m.getCreatedBy()).getName() : null))
                .toList();
        return new McpDeleteCheckResponse(denied.isEmpty(), denied);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<McpMaster> mcps = mcpMasterRepository.findByIdIn(ids);
        Map<Long, User> creators = loadCreators(mcps);
        boolean isAdmin = isAdmin(user);
        List<McpDeleteCheckResponse.DeniedMcp> denied = mcps.stream()
                .filter(m -> !isAdmin && !m.getCreatedBy().equals(user.getId()))
                .map(m -> new McpDeleteCheckResponse.DeniedMcp(
                        m.getId(), m.getMcpName(),
                        creators.get(m.getCreatedBy()) != null
                                ? creators.get(m.getCreatedBy()).getName() : null))
                .toList();
        if (!denied.isEmpty()) {
            // All-or-nothing: nothing is deleted.
            throw new BusinessException(BusinessErrorCode.MCP_DELETE_FORBIDDEN,
                    "You do not have permission to delete: "
                            + denied.stream().map(McpDeleteCheckResponse.DeniedMcp::name).toList());
        }
        for (McpMaster mcp : mcps) {
            mcp.setDeletedYn("Y");
            mcpMasterRepository.save(mcp);
            // Files + subscriptions are kept (soft delete) so that skills
            // pinning this MCP can still download its versioned files.
            auditService.log(user, "DELETE", "MCP", String.valueOf(mcp.getId()),
                    "name=" + mcp.getMcpName());
        }
    }

    // ------------------------------------------------------------------
    // View count (idempotent: 1 user / MCP / view-window)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int incrementView(Long mcpId, User viewer) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, viewer);

        Instant now = Instant.now();
        Optional<McpViewLog> last =
                mcpViewLogRepository.findTopByMcpIdAndUserIdOrderByViewedAtDesc(mcpId, viewer.getId());
        if (last.isEmpty()
                || ChronoUnit.MINUTES.between(last.get().getViewedAt(), now) >= viewWindowMinutes) {
            mcp.setViewCount(mcp.getViewCount() + 1);
            mcpMasterRepository.save(mcp);
            mcpViewLogRepository.save(McpViewLog.builder()
                    .mcpId(mcpId)
                    .userId(viewer.getId())
                    .viewedAt(now)
                    .build());
        }
        return mcp.getViewCount();
    }

    // ------------------------------------------------------------------
    // Download log (chart "total downloads" metric)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void recordDownload(Long mcpId, User user, boolean downloadAll) {
        mcpDownloadLogRepository.save(McpDownloadLog.builder()
                .mcpId(mcpId)
                .userId(user.getId())
                .downloadAll(downloadAll)
                .build());
        // Reputation (BA §9.5): a new download is a signal too — refresh the
        // cached score so popularity (download count) never goes stale until
        // the next rating.
        reputationService.refreshMcp(mcpId);
    }

    // ------------------------------------------------------------------
    // Export Excel (in-memory workbook — NASC-safe)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(McpSearchRequest request, List<Long> selectedIds, User viewer) {
        Specification<McpMaster> spec = Specification.allOf(
                McpMasterRepository.visibleTo(viewer),
                searchCriteria(request, viewer, true));
        if (selectedIds != null && !selectedIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("id").in(selectedIds));
        }
        List<McpMaster> mcps = mcpMasterRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Map<Long, User> creators = loadCreators(mcps);
        Map<Long, RatingAgg> ratings = loadRatings(mcps);
        Map<Long, List<McpFile>> filesByMcp = mcpFileRepository
                .findByMcpIdIn(mcps.stream().map(McpMaster::getId).toList()).stream()
                .filter(f -> "N".equals(f.getPendingYn()))
                .collect(Collectors.groupingBy(McpFile::getMcpId));

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("MCPs");
            ExcelExportStyle.freezeHeader(sheet);
            CellStyle headerStyle = ExcelExportStyle.headerStyle(workbook);
            CellStyle dataStyle = ExcelExportStyle.dataStyle(workbook);
            CellStyle wrapStyle = ExcelExportStyle.wrapStyle(workbook);

            String[] columns = {"MCP Name", "Code", "Description", "Creator", "Created Date",
                    "Status", "Views", "Rating", "Files"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                ExcelExportStyle.setStyled(header, i, columns[i], headerStyle);
            }
            int rowIdx = 1;
            for (McpMaster m : mcps) {
                Row row = sheet.createRow(rowIdx++);
                ExcelExportStyle.setStyled(row, 0, m.getMcpName(), dataStyle);
                ExcelExportStyle.setStyled(row, 1, m.getMcpCode(), dataStyle);
                ExcelExportStyle.setStyled(row, 2,
                        m.getDescription() != null ? m.getDescription() : "", wrapStyle);
                ExcelExportStyle.setStyled(row, 3, creators.get(m.getCreatedBy()) != null
                        ? creators.get(m.getCreatedBy()).getName() : "", dataStyle);
                ExcelExportStyle.setStyled(row, 4, m.getCreatedAt() != null
                        ? m.getCreatedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "", dataStyle);
                ExcelExportStyle.setStyled(row, 5, m.getStatus(), dataStyle);
                ExcelExportStyle.setStyled(row, 6, m.getViewCount(), dataStyle);
                RatingAgg agg = ratings.get(m.getId());
                ExcelExportStyle.setStyled(row, 7, agg != null
                        ? String.format("★ %.1f (%d)", agg.average(), agg.count())
                        : "No rating", dataStyle);
                ExcelExportStyle.setStyled(row, 8, filesByMcp.getOrDefault(m.getId(), List.of()).stream()
                        .map(McpFile::getFileName)
                        .collect(Collectors.joining(", ")), dataStyle);
            }
            // Column widths: fit content (min 12 chars), Description capped at 60 chars.
            ExcelExportStyle.autoSizeColumns(sheet, columns.length - 1, 60);
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Không thể tạo file export MCP", e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to export the MCP list.");
        }
    }

    // ------------------------------------------------------------------
    // Edit (optimistic lock + FIELD_READ_ONLY)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int update(Long mcpId, String name, String description,
                      String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                      MultipartFile[] files, List<Long> removeFileIds,
                      Instant expectedUpdatedAt, User user) {
        if (!isAdmin(user) && !rateLimiter.allow("mcp-edit:" + user.getId(),
                EDIT_MAX_PER_MINUTE, EDIT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.MCP_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        boolean isAdmin = isAdmin(user);
        boolean isCreator = mcp.getCreatedBy().equals(user.getId());

        if (!canManage(mcp, user)) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_EDITABLE,
                    "You do not have permission to edit this MCP.");
        }
        if (McpMaster.STATUS_PENDING_APPROVAL.equals(mcp.getStatus())) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_EDITABLE,
                    "An MCP pending approval cannot be edited.");
        }
        if (expectedUpdatedAt != null && !expectedUpdatedAt.equals(mcp.getUpdatedAt())) {
            throw new BusinessException(BusinessErrorCode.MCP_OPTIMISTIC_LOCK,
                    "MCP has been changed by another user.");
        }

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_REQUIRED,
                    "MCP name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_TOO_LONG,
                    "MCP name is at most 200 characters.");
        }
        boolean nameChanged = !trimmedName.equals(mcp.getMcpName());
        // APPROVED + non-admin: name is read-only.
        if (nameChanged && !isAdmin && McpMaster.STATUS_APPROVED.equals(mcp.getStatus())) {
            throw new BusinessException(BusinessErrorCode.MCP_FIELD_READ_ONLY,
                    "The MCP name cannot be changed for a published MCP.");
        }
        if (nameChanged && mcpMasterRepository.existsByMcpNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(
                trimmedName, mcpId, "N", mcp.getCreatedBy())) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_DUPLICATE,
                    "You already have an MCP with this name.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);

        boolean wasPublished = McpMaster.STATUS_APPROVED.equals(mcp.getStatus());

        mcp.setMcpName(trimmedName);
        mcp.setDescription(description == null || description.isBlank() ? null : description);
        mcp.setHashtags(hashtags);

        // Files are only managed through the version-update flow for approved
        // MCPs; for DRAFT/REJECTED the upload still appends live files and
        // removeFileIds removes live files (at least one must remain).
        if (!wasPublished) {
            List<Long> removeIds = removeFileIds == null ? List.of() : removeFileIds.stream().distinct().toList();
            if (!removeIds.isEmpty()) {
                List<McpFile> removable = mcpFileRepository.findAllById(removeIds).stream()
                        .filter(f -> f.getMcpId().equals(mcpId))
                        .filter(f -> "N".equals(f.getPendingYn()))
                        .toList();
                if (removable.size() != removeIds.size()) {
                    throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                            "One or more files to remove do not exist.");
                }
                long liveCount = mcpFileRepository.findByMcpIdAndPendingYn(mcpId, "N").size();
                if (liveCount - removeIds.size() + (files == null ? 0 : files.length) < 1) {
                    throw new BusinessException(BusinessErrorCode.MCP_FILE_REQUIRED,
                            "At least one file must remain.");
                }
                for (McpFile f : removable) {
                    mcpFileStorageService.delete(f.getFilePath());
                    mcpFileRepository.delete(f);
                }
            }
            storeFiles(mcpId, files);
        }
        storeCategoryMappings(mcpId, categories);
        storeStageMappings(mcpId, stages);

        // Version number only advances when a version update is approved.
        mcp.setUpdatedAt(Instant.now());
        mcpMasterRepository.save(mcp);

        auditService.log(user, "UPDATE", "MCP", String.valueOf(mcpId),
                "name=" + trimmedName + ", status=" + mcp.getStatus());

        if (wasPublished) {
            notifySubscribers(mcp, user);
        }
        return mcp.getVersion();
    }

    /** In-app + email notification to all subscribers of an edited published MCP. */
    private void notifySubscribers(McpMaster mcp, User actor) {
        List<McpSubscription> subs = mcpSubscriptionRepository.findByMcpId(mcp.getId());
        if (subs.isEmpty()) {
            return;
        }
        Map<Long, User> subscribers = userRepository.findByIdIn(
                        subs.stream().map(McpSubscription::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        if (subscribers.isEmpty()) {
            return;
        }
        Map<Long, UserSettings> settings =
                userSettingsRepository.findAllById(subscribers.keySet()).stream()
                        .collect(Collectors.toMap(UserSettings::getUserId, s -> s, (a, b) -> a));
        String title = "MCP \"" + mcp.getMcpName() + "\" has been edited information.";
        String mcpUrl = mcpDetailUrl(mcp.getId());
        String body = "The MCP \"" + mcp.getMcpName() + "\" you are subscribed has been edited information."
                + (mcpUrl != null ? "\n\n" + mcpUrl : "");
        eventNotificationService.notifyBatch(actor, subscribers, settings,
                subId -> new EventNotificationService.EventDetails(
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

    @Override
    @Transactional
    public void deleteFile(Long mcpId, Long fileId, User user) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!canManage(mcp, user)) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_EDITABLE,
                    "You do not have permission to edit this MCP.");
        }
        if (McpMaster.STATUS_APPROVED.equals(mcp.getStatus())) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_LOCKED,
                    "Files of an approved MCP can only be managed via Update Version.");
        }
        McpFile file = mcpFileRepository.findById(fileId)
                .filter(f -> f.getMcpId().equals(mcpId))
                .filter(f -> "N".equals(f.getPendingYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "MCP file does not exist."));
        mcpFileStorageService.delete(file.getFilePath());
        mcpFileRepository.delete(file);
        mcp.setUpdatedAt(Instant.now());
        mcpMasterRepository.save(mcp);
    }

    // ------------------------------------------------------------------
    // Version update (APPROVED MCPs — staged, review before going live)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void updateVersion(Long mcpId, String name, String description,
                              String hashtagsJson, List<Long> categoryIds, List<Long> stageIds,
                              MultipartFile[] files, List<Long> removeFileIds,
                              User user) {
        if (!isAdmin(user) && !rateLimiter.allow("mcp-version:" + user.getId(),
                VERSION_MAX_PER_MINUTE, VERSION_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.MCP_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!canManage(mcp, user)) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_EDITABLE,
                    "You do not have permission to update this MCP.");
        }
        if (!McpMaster.STATUS_APPROVED.equals(mcp.getStatus())) {
            throw new BusinessException(BusinessErrorCode.MCP_VERSION_UPDATE_NOT_ALLOWED,
                    "Only approved MCPs can receive a version update.");
        }
        if (mcp.getPendingMetadata() != null) {
            throw new BusinessException(BusinessErrorCode.MCP_VERSION_UPDATE_NOT_ALLOWED,
                    "A version update is already pending approval.");
        }
        long newFileCount = files == null ? 0
                : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).count();
        if (newFileCount == 0) {
            throw new BusinessException(BusinessErrorCode.MCP_VERSION_FILE_REQUIRED,
                    "At least one new file is required to update the version.");
        }

        // Same metadata rules as a regular edit (name IS changeable here —
        // it goes through review).
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_REQUIRED,
                    "MCP name is required.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_TOO_LONG,
                    "MCP name is at most 200 characters.");
        }
        if (!trimmedName.equals(mcp.getMcpName()) && mcpMasterRepository.existsByMcpNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(
                trimmedName, mcpId, "N", mcp.getCreatedBy())) {
            throw new BusinessException(BusinessErrorCode.MCP_NAME_DUPLICATE,
                    "You already have an MCP with this name.");
        }
        List<String> hashtags = parseAndValidateHashtags(hashtagsJson);
        List<Long> categories = validateCategoryIds(categoryIds, true);
        List<Long> stages = validateStageIds(stageIds, true);

        // Files to remove must be live files of this MCP.
        List<Long> removeIds = removeFileIds == null ? List.of() : removeFileIds.stream().distinct().toList();
        if (!removeIds.isEmpty()) {
            List<McpFile> removable = mcpFileRepository.findAllById(removeIds).stream()
                    .filter(f -> f.getMcpId().equals(mcpId))
                    .filter(f -> "N".equals(f.getPendingYn()))
                    .toList();
            if (removable.size() != removeIds.size()) {
                throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "One or more files to remove do not exist.");
            }
        }

        // Stage the update: pending files + staged metadata.
        storeFiles(mcpId, files, "Y");
        mcp.setPendingMetadata(new McpPendingMetadata(
                trimmedName,
                description == null || description.isBlank() ? null : description,
                hashtags, categories, stages));
        mcp.setPendingRemoveFileIds(removeIds);
        mcpMasterRepository.save(mcp);

        mcpWorkflowService.transition(mcpId, user, McpMaster.STATUS_PENDING_APPROVAL, null);
    }

    @Override
    @Transactional
    public void cancelVersionUpdate(Long mcpId, User user) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!canManage(mcp, user)) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_EDITABLE,
                    "You do not have permission to cancel this version update.");
        }
        if (!McpMaster.STATUS_PENDING_APPROVAL.equals(mcp.getStatus())
                || mcp.getPendingMetadata() == null) {
            throw new BusinessException(BusinessErrorCode.MCP_VERSION_UPDATE_NOT_ALLOWED,
                    "There is no pending version update to cancel.");
        }
        mcpVersionUpdateService.discardPendingVersion(mcp);
        mcp.setStatus(McpMaster.STATUS_APPROVED);
        mcpMasterRepository.save(mcp);
        mcpStatusHistoryRepository.save(McpStatusHistory.builder()
                .mcpId(mcpId)
                .fromStatus(McpMaster.STATUS_PENDING_APPROVAL)
                .toStatus(McpMaster.STATUS_APPROVED)
                .changedBy(user.getId())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<McpVersionItem> listVersions(Long mcpId, User viewer) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, viewer);
        Map<Long, User> creators = loadCreators(List.of(mcp));
        List<McpVersionItem> items = new ArrayList<>(mcpVersionRepository.findByMcpIdOrderByVersionDesc(mcpId).stream()
                .map(v -> new McpVersionItem(
                        v.getVersion(), v.getName(), v.getDescription(),
                        v.getHashtags() != null ? v.getHashtags() : List.of(),
                        creators.get(v.getCreatedBy()) != null ? creators.get(v.getCreatedBy()).getName() : null,
                        v.getCreatedAt(),
                        mcpVersionFileRepository.findByMcpVersionId(v.getId()).stream()
                                .map(f -> new McpVersionFileItem(f.getId(), f.getFileName(), f.getFileSize()))
                                .toList()))
                .toList());
        // The current version has no snapshot row until it is superseded —
        // prepend it (served from the live mcp_files) so the Versions tab
        // shows every downloadable version.
        items.add(0, new McpVersionItem(
                mcp.getVersion(), mcp.getMcpName(), mcp.getDescription(),
                mcp.getHashtags() != null ? mcp.getHashtags() : List.of(),
                creators.get(mcp.getCreatedBy()) != null ? creators.get(mcp.getCreatedBy()).getName() : null,
                mcp.getCreatedAt(),
                mcpFileRepository.findByMcpIdAndPendingYn(mcpId, "N").stream()
                        .map(f -> new McpVersionFileItem(f.getId(), f.getFileName(), f.getFileSize()))
                        .toList()));
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public McpVersionFileDownload downloadVersionFile(Long mcpId, int version, Long fileId, User viewer) {
        mcpAccessHelper.getVisibleIncludingDeleted(mcpId, viewer);
        McpVersion mv = findVersion(mcpId, version);
        // Current version (no snapshot): serve the live mcp_files row.
        if (mv.getId() == null) {
            McpFile f = mcpFileRepository.findById(fileId)
                    .filter(lf -> lf.getMcpId().equals(mcpId))
                    .filter(lf -> "N".equals(lf.getPendingYn()))
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_VERSION_FILE_NOT_FOUND,
                            "Version file does not exist."));
            return new McpVersionFileDownload(f.getFileName(), mcpFileStorageService.load(f.getFilePath()));
        }
        McpVersionFile vf = mcpVersionFileRepository.findById(fileId)
                .filter(f -> f.getMcpVersionId().equals(mv.getId()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_VERSION_FILE_NOT_FOUND,
                        "Version file does not exist."));
        return new McpVersionFileDownload(vf.getFileName(), mcpFileStorageService.load(vf.getFilePath()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<McpVersionFile> listVersionFiles(Long mcpId, int version, User viewer) {
        mcpAccessHelper.getVisibleIncludingDeleted(mcpId, viewer);
        McpVersion mv = findVersion(mcpId, version);
        // Current version (no snapshot): the live mcp_files ARE this version.
        if (mv.getId() == null) {
            List<McpVersionFile> files = mcpFileRepository.findByMcpIdAndPendingYn(mcpId, "N").stream()
                    .map(f -> McpVersionFile.builder()
                            .fileName(f.getFileName())
                            .filePath(f.getFilePath())
                            .fileSize(f.getFileSize())
                            .build())
                    .toList();
            if (files.isEmpty()) {
                throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "This version has no files.");
            }
            return files;
        }
        List<McpVersionFile> files = mcpVersionFileRepository.findByMcpVersionId(mv.getId());
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "This version has no files.");
        }
        return files;
    }

    /**
     * Finds one snapshot version of an MCP. The CURRENT version has no
     * snapshot row until a newer version is approved (snapshots are created
     * when a version is superseded) — so when the requested version equals
     * the master's current version and no snapshot exists, a synthetic
     * version is returned (its file list is the live mcp_files).
     */
    private McpVersion findVersion(Long mcpId, int version) {
        McpVersion snapshot = mcpVersionRepository
                .findByMcpIdOrderByVersionDesc(mcpId).stream()
                .filter(v -> v.getVersion() == version)
                .findFirst()
                .orElse(null);
        if (snapshot != null) {
            return snapshot;
        }
        McpMaster master = mcpMasterRepository.findById(mcpId).orElse(null);
        if (master != null && master.getVersion() == version) {
            return McpVersion.builder()
                    .mcpId(mcpId)
                    .version(version)
                    .name(master.getMcpName())
                    .build();
        }
        throw new BusinessException(BusinessErrorCode.MCP_VERSION_NOT_FOUND,
                "MCP version does not exist.");
    }

    // ------------------------------------------------------------------
    // Subscription
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void subscribe(Long mcpId, User user) {
        McpMaster mcp = mcpAccessHelper.getVisible(mcpId, user);
        if (!McpMaster.STATUS_APPROVED.equals(mcp.getStatus())
                && !mcp.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_APPROVED,
                    "Only published MCPs can be subscribed.");
        }
        if (mcp.getCreatedBy().equals(user.getId())) {
            throw new BusinessException(BusinessErrorCode.MCP_SUBSCRIBE_FORBIDDEN,
                    "You cannot subscribe to your own MCP.");
        }
        if (!mcpSubscriptionRepository.existsByMcpIdAndUserId(mcpId, user.getId())) {
            mcpSubscriptionRepository.save(
                    McpSubscription.builder()
                            .mcpId(mcpId).userId(user.getId()).build());
        }
    }

    @Override
    @Transactional
    public void unsubscribe(Long mcpId, User user) {
        mcpAccessHelper.getVisible(mcpId, user);
        mcpSubscriptionRepository.deleteByMcpIdAndUserId(mcpId, user.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean isAdmin(User user) {
        return user.getRole() != null && "ADMIN_USER".equals(user.getRole().getName());
    }

    /** Owner-like management rights: owner or admin. */
    private boolean canManage(McpMaster mcp, User user) {
        return isAdmin(user) || mcp.getCreatedBy().equals(user.getId());
    }

    private record RatingAgg(double average, long count) {
    }

    private Map<Long, User> loadCreators(List<McpMaster> mcps) {
        Set<Long> ids = mcps.stream().map(McpMaster::getCreatedBy).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, RatingAgg> loadRatings(List<McpMaster> mcps) {
        List<Long> ids = mcps.stream().map(McpMaster::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<McpRating> ratings = mcpRatingRepository.findByMcpIdIn(ids);
        return ratings.stream().collect(Collectors.groupingBy(
                McpRating::getMcpId,
                Collectors.reducing(
                        new RatingAgg(0, 0),
                        r -> new RatingAgg(r.getRating().doubleValue(), 1),
                        (a, b) -> new RatingAgg(a.average() + b.average(), a.count() + b.count()))))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new RatingAgg(e.getValue().average() / e.getValue().count(), e.getValue().count())));
    }

    private Map<Long, ReputationScore> loadReputations(List<McpMaster> mcps) {
        if (mcps.isEmpty()) {
            return Map.of();
        }
        return reputationService.getReputations("MCP",
                mcps.stream().map(McpMaster::getId).toList());
    }

    private Set<Long> loadSubscribed(List<McpMaster> mcps, Long viewerId) {
        List<Long> ids = mcps.stream().map(McpMaster::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return mcpSubscriptionRepository.findByUserId(viewerId).stream()
                .map(McpSubscription::getMcpId)
                .filter(ids::contains)
                .collect(Collectors.toSet());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
