package com.skillmanagement.skill.service.impl;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.dto.SkillTaxonomyItem;
import com.skillmanagement.skill.model.ProcessStage;
import com.skillmanagement.skill.model.SkillCategory;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.ProcessStageRepository;
import com.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.skillmanagement.skill.repository.SkillCategoryRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.skillmanagement.skill.service.SkillTaxonomyService;
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
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new BusinessException(BusinessErrorCode.SKILL_CATEGORY_NOT_FOUND,
                    "Category does not exist.");
        }
        categoryMappingRepository.deleteByCategoryIdIn(List.of(id));
        categoryRepository.deleteById(id);
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
    }

    @Override
    @Transactional
    public void deleteStage(Long id) {
        if (!stageRepository.existsById(id)) {
            throw new BusinessException(BusinessErrorCode.SKILL_STAGE_NOT_FOUND,
                    "Process stage does not exist.");
        }
        stageMappingRepository.deleteByStageIdIn(List.of(id));
        stageRepository.deleteById(id);
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
                .map(Skill::getId)
                .collect(Collectors.toSet());
    }
}
