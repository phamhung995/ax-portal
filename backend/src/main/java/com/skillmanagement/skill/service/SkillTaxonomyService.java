package com.skillmanagement.skill.service;

import com.skillmanagement.skill.dto.SkillTaxonomyItem;

import java.util.List;

/**
 * Admin management of skill categories and process stages.
 */
public interface SkillTaxonomyService {

    /** All categories with their skill usage count (not-deleted skills). */
    List<SkillTaxonomyItem> listCategories();

    /** All stages (pipeline order) with their skill usage count. */
    List<SkillTaxonomyItem> listStages();

    /** Creates a category; 409 SKILL_CATEGORY_DUPLICATE when the name exists. */
    Long createCategory(String name);

    /** Renames a category; 404 SKILL_CATEGORY_NOT_FOUND, 409 SKILL_CATEGORY_DUPLICATE. */
    void updateCategory(Long id, String name);

    /** Deletes a category and its skill mappings; 404 SKILL_CATEGORY_NOT_FOUND. */
    void deleteCategory(Long id);

    /** Creates a stage (appended to the end of the pipeline). */
    Long createStage(String name, String description);

    /** Renames / re-describes a stage; 404 SKILL_STAGE_NOT_FOUND, 409 SKILL_STAGE_DUPLICATE. */
    void updateStage(Long id, String name, String description);

    /** Deletes a stage and its skill mappings; 404 SKILL_STAGE_NOT_FOUND. */
    void deleteStage(Long id);
}
