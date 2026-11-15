package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.McpTaxonomyItem;

import java.util.List;

/**
 * Admin management of MCP categories and process stages (MCP's own taxonomy).
 */
public interface McpTaxonomyService {

    /** All categories with their MCP usage count (not-deleted MCPs). */
    List<McpTaxonomyItem> listCategories();

    /** All stages (pipeline order) with their MCP usage count. */
    List<McpTaxonomyItem> listStages();

    /** Creates a category; 409 MCP_CATEGORY_DUPLICATE when the name exists. */
    Long createCategory(String name);

    /** Renames a category; 404 MCP_CATEGORY_NOT_FOUND, 409 MCP_CATEGORY_DUPLICATE. */
    void updateCategory(Long id, String name);

    /** Deletes a category and its MCP mappings; 404 MCP_CATEGORY_NOT_FOUND. */
    void deleteCategory(Long id);

    /** Creates a stage (appended to the end of the pipeline). */
    Long createStage(String name, String description);

    /** Renames / re-describes a stage; 404 MCP_STAGE_NOT_FOUND, 409 MCP_STAGE_DUPLICATE. */
    void updateStage(Long id, String name, String description);

    /** Deletes a stage and its MCP mappings; 404 MCP_STAGE_NOT_FOUND. */
    void deleteStage(Long id);
}
