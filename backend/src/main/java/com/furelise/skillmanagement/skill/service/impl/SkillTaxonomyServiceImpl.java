package com.furelise.skillmanagement.skill.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.skill.dto.SkillTaxonomyItem;
import com.furelise.skillmanagement.skill.model.ProcessStage;
import com.furelise.skillmanagement.skill.model.SkillCategory;
import com.furelise.skillmanagement.skill.model.SkillCategoryMapping;
import com.furelise.skillmanagement.skill.model.SkillStageMapping;
import com.furelise.skillmanagement.skill.repository.ProcessStageRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.service.SkillTaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin management of skill categories and process stages.
 * Skill usage counts only consider not-deleted skills.
 */
@Service
@RequiredArgsConstructor
public class SkillTaxonomyServiceImpl implements SkillTaxonomyService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final SkillCategoryRepository categoryRepository;
    private final ProcessStageRepository stageRepository;
    private final SkillCategoryMappingRepository categoryMappingRepository;
    private final SkillStageMappingRepository stageMappingRepository;
    private final SkillRepository skillRepository;
    private final com.furelise.skillmanagement.audit.service.AuditService auditService;

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SkillTaxonomyItem> listCategories() {
        Set<Long> activeSkills = activeSkillIds();
        return categoryRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(c -> new SkillTaxonomyItem(
                        c.getId(), c.getName(), 0, null,
                        categoryMappingRepository.findByCategoryId(c.getId()).stream()
                                .filter(m -> activeSkills.contains(m.getSkillId()))
                                .count()))
                .toList();
    }

    @Override
    @Transactional
    public Long createCategory(String name) {
        String trimmed = normalizeName(name);
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_DUPLICATE,
                    "A category with this name already exists.");
        }
        SkillCategory saved = categoryRepository.save(SkillCategory.builder().name(trimmed).build());
        auditService.log("CREATE", "SKILL_CATEGORY", String.valueOf(saved.getId()), "name=" + trimmed);
        return saved.getId();
    }

    @Override
    @Transactional
    public void updateCategory(Long id, String name) {
        SkillCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_CATEGORY_NOT_FOUND,
                        "Category does not exist."));
        String trimmed = normalizeName(name);
        if (!trimmed.equalsIgnoreCase(category.getName())
                && categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_DUPLICATE,
                    "A category with this name already exists.");
        }
        category.setName(trimmed);
        categoryRepository.save(category);
        auditService.log("UPDATE", "SKILL_CATEGORY", String.valueOf(id), "name=" + trimmed);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        SkillCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_CATEGORY_NOT_FOUND,
                        "Category does not exist."));
        categoryMappingRepository.deleteByCategoryIdIn(List.of(id));
        categoryRepository.deleteById(id);
        auditService.log("DELETE", "SKILL_CATEGORY", String.valueOf(id), "name=" + category.getName());
    }

    // ------------------------------------------------------------------
    // Stages
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SkillTaxonomyItem> listStages() {
        Set<Long> activeSkills = activeSkillIds();
        return stageRepository.findAllByOrderBySortOrderAsc().stream()
                .map(s -> new SkillTaxonomyItem(
                        s.getId(), s.getName(), s.getSortOrder(), s.getDescription(),
                        stageMappingRepository.findByStageId(s.getId()).stream()
                                .filter(m -> activeSkills.contains(m.getSkillId()))
                                .count()))
                .toList();
    }

    @Override
    @Transactional
    public Long createStage(String name, String description) {
        String trimmed = normalizeName(name);
        if (stageRepository.existsByNameIgnoreCase(trimmed)) {
            throw new BusinessException(BusinessErrorCode.SKILL_STAGE_DUPLICATE,
                    "A process stage with this name already exists.");
        }
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_STAGE_DESCRIPTION_TOO_LONG,
                    "Description is at most 200 characters.");
        }
        int nextOrder = stageRepository.findAllByOrderBySortOrderAsc().stream()
                .mapToInt(ProcessStage::getSortOrder)
                .max()
                .orElse(0) + 1;
        ProcessStage saved = stageRepository.save(
                ProcessStage.builder().name(trimmed).description(desc == null || desc.isEmpty() ? null : desc)
                        .sortOrder(nextOrder).build());
        auditService.log("CREATE", "PROCESS_STAGE", String.valueOf(saved.getId()), "name=" + trimmed);
        return saved.getId();
    }

    @Override
    @Transactional
    public void updateStage(Long id, String name, String description) {
        ProcessStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_STAGE_NOT_FOUND,
                        "Process stage does not exist."));
        String trimmed = normalizeName(name);
        if (!trimmed.equalsIgnoreCase(stage.getName())
                && stageRepository.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new BusinessException(BusinessErrorCode.SKILL_STAGE_DUPLICATE,
                    "A process stage with this name already exists.");
        }
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_STAGE_DESCRIPTION_TOO_LONG,
                    "Description is at most 200 characters.");
        }
        stage.setName(trimmed);
        stage.setDescription(desc == null || desc.isEmpty() ? null : desc);
        stageRepository.save(stage);
        auditService.log("UPDATE", "PROCESS_STAGE", String.valueOf(id), "name=" + trimmed);
    }

    @Override
    @Transactional
    public void deleteStage(Long id) {
        ProcessStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SKILL_STAGE_NOT_FOUND,
                        "Process stage does not exist."));
        stageMappingRepository.deleteByStageIdIn(List.of(id));
        stageRepository.deleteById(id);
        auditService.log("DELETE", "PROCESS_STAGE", String.valueOf(id), "name=" + stage.getName());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_NAME_REQUIRED,
                    "Name is required.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_NAME_TOO_LONG,
                    "Name is at most 50 characters.");
        }
        return trimmed;
    }

    /** Ids of all not-deleted skills (usage counts exclude soft-deleted skills). */
    private Set<Long> activeSkillIds() {
        return skillRepository.findAll().stream()
                .filter(s -> "N".equals(s.getDeletedYn()))
                .map(com.furelise.skillmanagement.skill.model.Skill::getId)
                .collect(Collectors.toSet());
    }
}
