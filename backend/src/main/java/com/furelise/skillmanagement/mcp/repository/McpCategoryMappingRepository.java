package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for mcp_category_mapping.
 */
public interface McpCategoryMappingRepository
        extends JpaRepository<McpCategoryMapping, McpCategoryMapping.McpCategoryMappingKey> {

    List<McpCategoryMapping> findByMcpIdIn(Collection<Long> mcpIds);

    List<McpCategoryMapping> findByMcpId(Long mcpId);

    List<McpCategoryMapping> findByCategoryId(Long categoryId);

    void deleteByMcpId(Long mcpId);

    void deleteByCategoryIdIn(Collection<Long> categoryIds);

    long countByCategoryId(Long categoryId);

    /** Groups the mappings of the given MCPs by MCP id. */
    static Map<Long, List<Long>> groupByMcp(List<McpCategoryMapping> mappings) {
        return mappings.stream().collect(Collectors.groupingBy(
                McpCategoryMapping::getMcpId,
                Collectors.mapping(McpCategoryMapping::getCategoryId, Collectors.toList())));
    }
}
