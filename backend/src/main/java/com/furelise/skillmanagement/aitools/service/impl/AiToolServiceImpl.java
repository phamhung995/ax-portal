package com.furelise.skillmanagement.aitools.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.aitools.dto.AiToolDetailResponse;
import com.furelise.skillmanagement.aitools.dto.AiToolFileItem;
import com.furelise.skillmanagement.aitools.dto.AiToolListItem;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchRequest;
import com.furelise.skillmanagement.aitools.dto.AiToolSearchResult;
import com.furelise.skillmanagement.aitools.model.AiTool;
import com.furelise.skillmanagement.aitools.model.AiToolCategoryMapping;
import com.furelise.skillmanagement.aitools.model.AiToolDownloadLog;
import com.furelise.skillmanagement.aitools.model.AiToolFile;
import com.furelise.skillmanagement.aitools.model.AiToolStageMapping;
import com.furelise.skillmanagement.aitools.repository.AiToolCategoryMappingRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolDownloadLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolFileRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolStageMappingRepository;
import com.furelise.skillmanagement.aitools.service.AiToolFileStorageService;
import com.furelise.skillmanagement.aitools.service.AiToolService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.model.ProcessStage;
import com.furelise.skillmanagement.skill.model.SkillCategory;
import com.furelise.skillmanagement.skill.repository.ProcessStageRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Tools service (mirrors the Skill Management search/stage-counts pattern:
 * the Process Map pipeline totals are computed WITHOUT the stageId filter so
 * the stage counts stay stable when a stage is selected).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolServiceImpl implements AiToolService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final AiToolRepository aiToolRepository;
    private final AiToolFileRepository aiToolFileRepository;
    private final AiToolCategoryMappingRepository categoryMappingRepository;
    private final AiToolStageMappingRepository stageMappingRepository;
    private final AiToolDownloadLogRepository downloadLogRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final ProcessStageRepository processStageRepository;
    private final UserRepository userRepository;
    private final AiToolFileStorageService fileStorageService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ReputationService reputationService;

    // ------------------------------------------------------------------
    // Create: any authenticated user. Update / delete: uploader or admin
    // (enforced in checkEditable).
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Long create(String name, String description, String tagsJson,
                       List<Long> categoryIds, List<Long> stageIds,
                       MultipartFile file, User creator) {
        String trimmedName = requireName(name);
        if (aiToolRepository.existsByNameIgnoreCaseAndDeletedYn(trimmedName, AiTool.NOT_DELETED)) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_NAME_DUPLICATE,
                    "An AI tool with this name already exists.");
        }
        List<String> tags = parseAndValidateTags(tagsJson);
        List<Long> categories = validateCategoryIds(categoryIds);
        List<Long> stages = validateStageIds(stageIds);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_REQUIRED,
                    "A file is required.");
        }

        AiTool tool = AiTool.builder()
                .name(trimmedName)
                .description(description == null || description.isBlank() ? null : description)
                .tags(tags)
                .createdBy(creator.getId())
                .build();
        AiTool saved = aiToolRepository.save(tool);
        storeFile(saved.getId(), file);
        storeCategoryMappings(saved.getId(), categories);
        storeStageMappings(saved.getId(), stages);
        auditService.log(creator, "CREATE", "AI_TOOL", String.valueOf(saved.getId()),
                "name=" + saved.getName());
        return saved.getId();
    }

    @Override
    @Transactional
    public void update(Long toolId, String name, String description, String tagsJson,
                       List<Long> categoryIds, List<Long> stageIds,
                       boolean removeFile, MultipartFile file, User user) {
        AiTool tool = getActive(toolId);
        checkEditable(tool, user);
        String trimmedName = requireName(name);
        if (!trimmedName.equalsIgnoreCase(tool.getName())
                && aiToolRepository.existsByNameIgnoreCaseAndDeletedYn(trimmedName, AiTool.NOT_DELETED)) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_NAME_DUPLICATE,
                    "An AI tool with this name already exists.");
        }
        tool.setName(trimmedName);
        tool.setDescription(description == null || description.isBlank() ? null : description);
        tool.setTags(parseAndValidateTags(tagsJson));
        tool.setUpdatedAt(Instant.now());
        aiToolRepository.save(tool);

        // File handling: a new file replaces the old one; removeFile deletes
        // it (a simultaneously provided new file is stored instead).
        if (file != null && !file.isEmpty()) {
            for (AiToolFile old : aiToolFileRepository.findByToolId(toolId)) {
                fileStorageService.delete(old.getFilePath());
            }
            aiToolFileRepository.deleteByToolId(toolId);
            storeFile(toolId, file);
        } else if (removeFile) {
            for (AiToolFile old : aiToolFileRepository.findByToolId(toolId)) {
                fileStorageService.delete(old.getFilePath());
            }
            aiToolFileRepository.deleteByToolId(toolId);
        }

        categoryMappingRepository.deleteByToolId(toolId);
        storeCategoryMappings(toolId, validateCategoryIds(categoryIds));
        stageMappingRepository.deleteByToolId(toolId);
        storeStageMappings(toolId, validateStageIds(stageIds));
        auditService.log(user, "UPDATE", "AI_TOOL", String.valueOf(toolId),
                "name=" + tool.getName());
    }

    @Override
    @Transactional
    public AiToolDeleteResult deleteBatch(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty()) {
            return new AiToolDeleteResult(0, List.of());
        }
        List<AiTool> tools = aiToolRepository.findByIdIn(ids).stream()
                .filter(t -> AiTool.NOT_DELETED.equals(t.getDeletedYn()))
                .toList();
        List<String> skippedNames = new ArrayList<>();
        int deleted = 0;
        for (AiTool tool : tools) {
            if (!canEdit(tool, user)) {
                skippedNames.add(tool.getName());
                continue;
            }
            tool.setDeletedYn(AiTool.DELETED);
            tool.setUpdatedAt(Instant.now());
            aiToolRepository.save(tool);
            for (AiToolFile f : aiToolFileRepository.findByToolId(tool.getId())) {
                fileStorageService.delete(f.getFilePath());
            }
            aiToolFileRepository.deleteByToolId(tool.getId());
            categoryMappingRepository.deleteByToolId(tool.getId());
            stageMappingRepository.deleteByToolId(tool.getId());
            auditService.log(user, "DELETE", "AI_TOOL", String.valueOf(tool.getId()),
                    "name=" + tool.getName());
            deleted++;
        }
        return new AiToolDeleteResult(deleted, skippedNames);
    }

    // ------------------------------------------------------------------
    // Search (grid, paged)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AiToolSearchResult search(AiToolSearchRequest request, User viewer) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();
        Sort.Direction direction = "ASC".equalsIgnoreCase(request.sortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = switch (request.sortBy() == null ? "" : request.sortBy().toLowerCase(Locale.ROOT)) {
            case "name" -> "name";
            case "uploader" -> "uploader";
            case "downloadcount" -> "downloadCount";
            case "reputation" -> "reputation";
            default -> "createdAt";
        };

        Specification<AiTool> spec = searchCriteria(request, true);

        // "uploader" sorts by the uploader's user name (join), and
        // "downloadCount" / "reputation" have no entity column (download log /
        // cached reputation score) — all are handled in memory, then paged
        // manually (same approach as the Skill Management avgRating sort).
        List<AiTool> tools;
        long total;
        if ("uploader".equals(sortBy) || "downloadCount".equals(sortBy) || "reputation".equals(sortBy)) {
            List<AiTool> all = aiToolRepository.findAll(spec,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            Map<Long, User> uploaders = loadUploaders(all);
            Map<Long, Long> downloads = loadDownloadCounts(all);
            Comparator<AiTool> bySort;
            if ("reputation".equals(sortBy)) {
                // Cached reputation_scores (null = not enough downloads yet,
                // sorts last). Always best-first — the direction toggle does
                // not apply to reputation (same as MCP).
                Map<Long, ReputationScore> scores = reputationService.getReputations(
                        "AI_TOOL", all.stream().map(AiTool::getId).toList());
                bySort = Comparator.comparingDouble(
                        (AiTool t) -> scores.get(t.getId()) != null
                                && scores.get(t.getId()).getScore() != null
                                ? scores.get(t.getId()).getScore().doubleValue() : -1.0).reversed();
            } else {
                bySort = "uploader".equals(sortBy)
                        ? Comparator.comparing(t -> {
                            User u = uploaders.get(t.getCreatedBy());
                            return u != null && u.getName() != null ? u.getName() : "";
                        })
                        : Comparator.comparingLong(t -> downloads.getOrDefault(t.getId(), 0L));
                if (direction == Sort.Direction.DESC) {
                    bySort = bySort.reversed();
                }
            }
            all.sort(bySort.thenComparing(AiTool::getCreatedAt,
                    direction == Sort.Direction.ASC ? Comparator.naturalOrder() : Comparator.reverseOrder())
                    .thenComparing(AiTool::getId));
            total = all.size();
            int from = Math.min((page - 1) * pageSize, all.size());
            int to = Math.min(from + pageSize, all.size());
            tools = List.copyOf(all.subList(from, to));
        } else {
            // PK tie-break: deterministic page boundaries (same rule as Skill list).
            Page<AiTool> result = aiToolRepository.findAll(spec,
                    PageRequest.of(page - 1, pageSize,
                            Sort.by(direction, sortBy).and(Sort.by("id"))));
            total = result.getTotalElements();
            tools = result.getContent();
        }

        Map<Long, User> uploaders = loadUploaders(tools);
        Map<Long, List<String>> categoriesByTool = loadCategoryNames(tools);
        Map<Long, List<String>> stagesByTool = loadStageNames(tools);
        Map<Long, Long> downloads = loadDownloadCounts(tools);
        Map<Long, AiToolFile> files = loadFiles(tools);
        Map<Long, ReputationScore> reputations = loadReputations(tools);

        List<AiToolListItem> items = tools.stream().map(t -> {
            User uploader = uploaders.get(t.getCreatedBy());
            AiToolFile file = files.get(t.getId());
            return new AiToolListItem(
                    t.getId(), t.getName(), t.getDescription(),
                    t.getCreatedBy(),
                    uploader != null ? uploader.getName() : null,
                    t.getCreatedAt(),
                    categoriesByTool.getOrDefault(t.getId(), List.of()),
                    stagesByTool.getOrDefault(t.getId(), List.of()),
                    t.getTags() == null ? List.of() : t.getTags(),
                    downloads.getOrDefault(t.getId(), 0L),
                    file != null ? file.getFileName() : null,
                    file != null ? file.getId() : null,
                    t.getCreatedBy().equals(viewer.getId()) || isAdmin(viewer),
                    reputations.get(t.getId()) != null && reputations.get(t.getId()).getScore() != null
                            ? reputations.get(t.getId()).getScore().doubleValue() : null,
                    reputations.get(t.getId()) != null ? reputations.get(t.getId()).getTier() : "NEW");
        }).toList();

        return new AiToolSearchResult(total, page, pageSize, items,
                stageCounts(request));
    }

    /**
     * Total tools per process stage under the given criteria (no paging) —
     * the Process Map pipeline indicators. The stageId filter itself is
     * EXCLUDED so the pipeline totals stay stable when a stage is selected.
     * A tool mapped to several stages counts once per stage.
     */
    private Map<Long, Long> stageCounts(AiToolSearchRequest request) {
        List<AiTool> all = aiToolRepository.findAll(
                searchCriteria(request, false), Sort.by(Sort.Direction.DESC, "createdAt"));
        if (all.isEmpty()) {
            return Map.of();
        }
        List<AiToolStageMapping> mappings = stageMappingRepository.findByToolIdIn(
                all.stream().map(AiTool::getId).toList());
        return mappings.stream()
                .collect(Collectors.groupingBy(AiToolStageMapping::getStageId, Collectors.counting()));
    }

    /**
     * Search criteria (AND between fields, OR within a multi-value field).
     *
     * @param includeStageId whether to apply the stageId filter (false for the
     *                       Process Map pipeline totals, which must stay
     *                       stable when a stage is selected)
     */
    private Specification<AiTool> searchCriteria(AiToolSearchRequest r, boolean includeStageId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deletedYn"), AiTool.NOT_DELETED));

            if (notBlank(r.name())) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + r.name().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (r.tags() != null && !r.tags().isEmpty()) {
                List<String> lowerTags = r.tags().stream()
                        .map(t -> t.trim().toLowerCase(Locale.ROOT))
                        .filter(t -> !t.isEmpty())
                        .toList();
                if (!lowerTags.isEmpty()) {
                    predicates.add(tagsContains(root, cb, lowerTags));
                }
            }
            if (r.uploaderId() != null) {
                predicates.add(cb.equal(root.get("createdBy"), r.uploaderId()));
            }
            if (r.createdDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        r.createdDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (r.createdDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        r.createdDateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (r.categoryIds() != null && !r.categoryIds().isEmpty()) {
                predicates.add(hasCategory(root, query, cb, r.categoryIds()));
            }
            if (includeStageId && r.stageId() != null) {
                predicates.add(hasStage(root, query, cb, r.stageId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** EXISTS a mapping of the tool to any of the given categories. */
    private Predicate hasCategory(Root<AiTool> root, CriteriaQuery<?> query,
                                  CriteriaBuilder cb, List<Long> categoryIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<AiToolCategoryMapping> m = sq.from(AiToolCategoryMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("toolId"), root.get("id")),
                m.get("categoryId").in(categoryIds));
        return cb.exists(sq);
    }

    /** EXISTS a mapping of the tool to the given process stage. */
    private Predicate hasStage(Root<AiTool> root, CriteriaQuery<?> query,
                               CriteriaBuilder cb, Long stageId) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<AiToolStageMapping> m = sq.from(AiToolStageMapping.class);
        sq.select(cb.literal(1L)).where(
                cb.equal(m.get("toolId"), root.get("id")),
                cb.equal(m.get("stageId"), stageId));
        return cb.exists(sq);
    }

    /**
     * Matches any of the given tags inside the JSONB array.
     * Same approach as Skill Management: lower(text(tags)) LIKE '%"tag"%'
     * (OR across tags). Tags are validated as [A-Za-z0-9_-], so they cannot
     * contain quote or LIKE-wildcard characters.
     */
    private Predicate tagsContains(Root<AiTool> root, CriteriaBuilder cb, List<String> lowerTags) {
        Expression<String> asText = cb.lower(
                cb.function("text", String.class, root.get("tags")));
        Predicate[] likes = lowerTags.stream()
                .map(t -> cb.like(asText, "%\"" + t + "\"%"))
                .toArray(Predicate[]::new);
        return cb.or(likes);
    }

    // ------------------------------------------------------------------
    // Detail / download
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AiToolDetailResponse getDetail(Long toolId, User viewer) {
        AiTool tool = getActive(toolId);
        User uploader = userRepository.findById(tool.getCreatedBy()).orElse(null);
        List<AiToolFile> files = aiToolFileRepository.findByToolId(toolId);
        AiToolFile file = files.isEmpty() ? null : files.get(0);
        long downloads = downloadLogRepository.findByToolIdIn(List.of(toolId)).size();

        return new AiToolDetailResponse(
                tool.getId(), tool.getName(), tool.getDescription(),
                tool.getCreatedBy(),
                uploader != null ? uploader.getName() : null,
                tool.getCreatedAt(), tool.getUpdatedAt(),
                categoryNamesOf(toolId), stageNamesOf(toolId),
                tool.getTags() == null ? List.of() : tool.getTags(),
                downloads,
                file != null
                        ? new AiToolFileItem(file.getId(), file.getFileName(),
                        file.getFileSize() == null ? 0L : file.getFileSize(), file.getCreatedAt())
                        : null,
                tool.getCreatedBy().equals(viewer.getId()) || isAdmin(viewer));
    }

    @Override
    @Transactional
    public AiToolDownloadResult download(Long toolId, Long fileId, User user) {
        AiTool tool = getActive(toolId);
        AiToolFile file = aiToolFileRepository.findById(fileId)
                .filter(f -> f.getToolId().equals(toolId))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.AI_TOOL_FILE_NOT_FOUND,
                        "AI tool file does not exist."));
        downloadLogRepository.save(AiToolDownloadLog.builder()
                .toolId(toolId)
                .userId(user.getId())
                .build());
        // Reputation (BA §9.5): a new download is a signal too — refresh the
        // cached score so popularity (download count) never goes stale.
        reputationService.refreshAiTool(toolId);
        return new AiToolDownloadResult(file.getFileName(),
                fileStorageService.load(file.getFilePath()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AiTool getActive(Long toolId) {
        AiTool tool = aiToolRepository.findById(toolId)
                .filter(t -> AiTool.NOT_DELETED.equals(t.getDeletedYn()))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.AI_TOOL_NOT_FOUND,
                        "AI tool does not exist."));
        return tool;
    }

    /** Only the uploader (creator) or an admin may edit/delete the tool. */
    private void checkEditable(AiTool tool, User user) {
        if (!canEdit(tool, user)) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_NOT_EDITABLE,
                    "Only the uploader or an admin can modify this tool.");
        }
    }

    private boolean canEdit(AiTool tool, User user) {
        return tool.getCreatedBy().equals(user.getId()) || isAdmin(user);
    }

    private static boolean isAdmin(User user) {
        return user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_NAME_REQUIRED,
                    "Tool name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_NAME_TOO_LONG,
                    "Tool name is at most " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private List<String> parseAndValidateTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        List<String> raw;
        try {
            raw = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_INVALID_TAG,
                    "Invalid tags payload.");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tag : raw) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String t = tag.trim().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > MAX_TAG_LENGTH) {
                throw new BusinessException(BusinessErrorCode.AI_TOOL_TAG_TOO_LONG,
                        "A tag is at most " + MAX_TAG_LENGTH + " characters.");
            }
            if (!TAG_PATTERN.matcher(t).matches()) {
                throw new BusinessException(BusinessErrorCode.AI_TOOL_INVALID_TAG,
                        "Tags may contain letters, digits, '-' and '_' only.");
            }
            if (!seen.add(t)) {
                continue;
            }
            result.add(t);
            if (result.size() > MAX_TAGS) {
                throw new BusinessException(BusinessErrorCode.AI_TOOL_TAGS_TOO_MANY,
                        "At most " + MAX_TAGS + " tags are allowed.");
            }
        }
        return result;
    }

    /** Validates the given category ids against the master table (required). */
    private List<Long> validateCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_CATEGORY_REQUIRED,
                    "At least one category is required.");
        }
        Set<Long> valid = skillCategoryRepository.findAll().stream()
                .map(SkillCategory::getId)
                .collect(Collectors.toSet());
        return categoryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(valid::contains)
                .toList();
    }

    /** Validates the given stage ids against the master table (required). */
    private List<Long> validateStageIds(List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_STAGE_REQUIRED,
                    "At least one process stage is required.");
        }
        Set<Long> valid = processStageRepository.findAll().stream()
                .map(ProcessStage::getId)
                .collect(Collectors.toSet());
        return stageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(valid::contains)
                .toList();
    }

    private void storeFile(Long toolId, MultipartFile file) {
        String relativePath = fileStorageService.store(toolId, file);
        aiToolFileRepository.save(AiToolFile.builder()
                .toolId(toolId)
                .fileName(file.getOriginalFilename() == null ? relativePath : file.getOriginalFilename())
                .filePath(relativePath)
                .fileSize(file.getSize())
                .build());
    }

    private void storeCategoryMappings(Long toolId, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            categoryMappingRepository.save(AiToolCategoryMapping.builder()
                    .toolId(toolId).categoryId(categoryId).build());
        }
    }

    private void storeStageMappings(Long toolId, List<Long> stageIds) {
        for (Long stageId : stageIds) {
            stageMappingRepository.save(AiToolStageMapping.builder()
                    .toolId(toolId).stageId(stageId).build());
        }
    }

    /** Category names of one tool. */
    private List<String> categoryNamesOf(Long toolId) {
        List<Long> categoryIds = categoryMappingRepository.findByToolId(toolId).stream()
                .map(AiToolCategoryMapping::getCategoryId)
                .toList();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillCategory> byId = skillCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        return categoryIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(SkillCategory::getName)
                .toList();
    }

    /** Stage names of one tool, in pipeline order. */
    private List<String> stageNamesOf(Long toolId) {
        List<Long> stageIds = stageMappingRepository.findByToolId(toolId).stream()
                .map(AiToolStageMapping::getStageId)
                .toList();
        if (stageIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProcessStage> byId = processStageRepository.findAllById(stageIds).stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        return stageIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProcessStage::getSortOrder))
                .map(ProcessStage::getName)
                .toList();
    }

    private Map<Long, User> loadUploaders(List<AiTool> tools) {
        Set<Long> ids = tools.stream().map(AiTool::getCreatedBy).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, List<String>> loadCategoryNames(List<AiTool> tools) {
        List<Long> ids = tools.stream().map(AiTool::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SkillCategory> byId = skillCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        return categoryMappingRepository.findByToolIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        AiToolCategoryMapping::getToolId,
                        Collectors.mapping(m -> {
                            SkillCategory c = byId.get(m.getCategoryId());
                            return c != null ? c.getName() : "";
                        }, Collectors.toList())));
    }

    private Map<Long, List<String>> loadStageNames(List<AiTool> tools) {
        List<Long> ids = tools.stream().map(AiTool::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProcessStage> byId = processStageRepository.findAll().stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        return stageMappingRepository.findByToolIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        AiToolStageMapping::getToolId,
                        Collectors.mapping(m -> {
                            ProcessStage s = byId.get(m.getStageId());
                            return s != null ? s.getName() : "";
                        }, Collectors.toList())));
    }

    private Map<Long, Long> loadDownloadCounts(List<AiTool> tools) {
        List<Long> ids = tools.stream().map(AiTool::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return downloadLogRepository.findByToolIdIn(ids).stream()
                .collect(Collectors.groupingBy(AiToolDownloadLog::getToolId, Collectors.counting()));
    }

    private Map<Long, ReputationScore> loadReputations(List<AiTool> tools) {
        if (tools.isEmpty()) {
            return Map.of();
        }
        return reputationService.getReputations("AI_TOOL",
                tools.stream().map(AiTool::getId).toList());
    }

    /** One file (id + name) per tool id. */
    private Map<Long, AiToolFile> loadFiles(List<AiTool> tools) {
        List<Long> ids = tools.stream().map(AiTool::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return aiToolFileRepository.findByToolIdIn(ids).stream()
                .collect(Collectors.toMap(AiToolFile::getToolId, f -> f, (a, b) -> a));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
