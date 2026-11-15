package com.furelise.skillmanagement.mcp.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpTaxonomyItem;
import com.furelise.skillmanagement.mcp.model.McpCategory;
import com.furelise.skillmanagement.mcp.model.McpCategoryMapping;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpStage;
import com.furelise.skillmanagement.mcp.model.McpStageMapping;
import com.furelise.skillmanagement.mcp.repository.McpCategoryMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpCategoryRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpStageMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpStageRepository;
import com.furelise.skillmanagement.mcp.service.McpTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin management of MCP categories and process stages (MCP's own taxonomy).
 * MCP usage counts only consider not-deleted MCPs.
 */
@Service
@RequiredArgsConstructor
public class McpTaxonomyServiceImpl implements McpTaxonomyService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final McpCategoryRepository categoryRepository;
    private final McpStageRepository stageRepository;
    private final McpCategoryMappingRepository categoryMappingRepository;
    private final McpStageMappingRepository stageMappingRepository;
    private final McpMasterRepository mcpMasterRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<McpTaxonomyItem> listCategories() {
        Set<Long> activeMcps = activeMcpIds();
        return categoryRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(c -> new McpTaxonomyItem(
                        c.getId(), c.getName(), 0, null,
                        categoryMappingRepository.findByCategoryId(c.getId()).stream()
                                .filter(m -> activeMcps.contains(m.getMcpId()))
                                .count()))
                .toList();
    }

    @Override
    @Transactional
    public Long createCategory(String name) {
        String trimmed = normalizeName(name);
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessException(BusinessErrorCode.MCP_CATEGORY_DUPLICATE,
                    "A category with this name already exists.");
        }
        McpCategory saved = categoryRepository.save(McpCategory.builder().name(trimmed).build());
        auditService.log("CREATE", "MCP_CATEGORY", String.valueOf(saved.getId()), "name=" + trimmed);
        return saved.getId();
    }

    @Override
    @Transactional
    public void updateCategory(Long id, String name) {
        McpCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_CATEGORY_NOT_FOUND,
                        "Category does not exist."));
        String trimmed = normalizeName(name);
        if (!trimmed.equalsIgnoreCase(category.getName())
                && categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new BusinessException(BusinessErrorCode.MCP_CATEGORY_DUPLICATE,
                    "A category with this name already exists.");
        }
        category.setName(trimmed);
        categoryRepository.save(category);
        auditService.log("UPDATE", "MCP_CATEGORY", String.valueOf(id), "name=" + trimmed);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        McpCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_CATEGORY_NOT_FOUND,
                        "Category does not exist."));
        categoryMappingRepository.deleteByCategoryIdIn(List.of(id));
        categoryRepository.deleteById(id);
        auditService.log("DELETE", "MCP_CATEGORY", String.valueOf(id), "name=" + category.getName());
    }

    // ------------------------------------------------------------------
    // Stages
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<McpTaxonomyItem> listStages() {
        Set<Long> activeMcps = activeMcpIds();
        return stageRepository.findAllByOrderBySortOrderAsc().stream()
                .map(s -> new McpTaxonomyItem(
                        s.getId(), s.getName(), s.getSortOrder(), s.getDescription(),
                        stageMappingRepository.findByStageId(s.getId()).stream()
                                .filter(m -> activeMcps.contains(m.getMcpId()))
                                .count()))
                .toList();
    }

    @Override
    @Transactional
    public Long createStage(String name, String description) {
        String trimmed = normalizeName(name);
        if (stageRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessException(BusinessErrorCode.MCP_STAGE_DUPLICATE,
                    "A process stage with this name already exists.");
        }
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_STAGE_DESCRIPTION_TOO_LONG,
                    "Description is at most 200 characters.");
        }
        int nextOrder = stageRepository.findAllByOrderBySortOrderAsc().stream()
                .mapToInt(McpStage::getSortOrder)
                .max()
                .orElse(0) + 1;
        McpStage saved = stageRepository.save(
                McpStage.builder().name(trimmed).description(desc == null || desc.isEmpty() ? null : desc)
                        .sortOrder(nextOrder).build());
        auditService.log("CREATE", "MCP_STAGE", String.valueOf(saved.getId()), "name=" + trimmed);
        return saved.getId();
    }

    @Override
    @Transactional
    public void updateStage(Long id, String name, String description) {
        McpStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_STAGE_NOT_FOUND,
                        "Process stage does not exist."));
        String trimmed = normalizeName(name);
        if (!trimmed.equalsIgnoreCase(stage.getName())
                && stageRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new BusinessException(BusinessErrorCode.MCP_STAGE_DUPLICATE,
                    "A process stage with this name already exists.");
        }
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_STAGE_DESCRIPTION_TOO_LONG,
                    "Description is at most 200 characters.");
        }
        stage.setName(trimmed);
        stage.setDescription(desc == null || desc.isEmpty() ? null : desc);
        stageRepository.save(stage);
        auditService.log("UPDATE", "MCP_STAGE", String.valueOf(id), "name=" + trimmed);
    }

    @Override
    @Transactional
    public void deleteStage(Long id) {
        McpStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_STAGE_NOT_FOUND,
                        "Process stage does not exist."));
        stageMappingRepository.deleteByStageIdIn(List.of(id));
        stageRepository.deleteById(id);
        auditService.log("DELETE", "MCP_STAGE", String.valueOf(id), "name=" + stage.getName());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_CATEGORY_NAME_REQUIRED,
                    "Name is required.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.MCP_CATEGORY_NAME_TOO_LONG,
                    "Name is at most 50 characters.");
        }
        return trimmed;
    }

    /** Ids of all not-deleted MCPs (usage counts exclude soft-deleted MCPs). */
    private Set<Long> activeMcpIds() {
        return mcpMasterRepository.findAll().stream()
                .filter(m -> "N".equals(m.getDeletedYn()))
                .map(McpMaster::getId)
                .collect(Collectors.toSet());
    }
}
