package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpStageMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for mcp_stage_mapping.
 */
public interface McpStageMappingRepository
        extends JpaRepository<McpStageMapping, McpStageMapping.McpStageMappingKey> {

    List<McpStageMapping> findByMcpIdIn(Collection<Long> mcpIds);

    List<McpStageMapping> findByMcpId(Long mcpId);

    List<McpStageMapping> findByStageId(Long stageId);

    void deleteByMcpId(Long mcpId);

    void deleteByStageIdIn(Collection<Long> stageIds);

    long countByStageId(Long stageId);

    /** Groups the mappings of the given MCPs by MCP id. */
    static Map<Long, List<Long>> groupByMcp(List<McpStageMapping> mappings) {
        return mappings.stream().collect(Collectors.groupingBy(
                McpStageMapping::getMcpId,
                Collectors.mapping(McpStageMapping::getStageId, Collectors.toList())));
    }
}
