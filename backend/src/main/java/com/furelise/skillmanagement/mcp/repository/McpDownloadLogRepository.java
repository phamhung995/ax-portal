package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpDownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface McpDownloadLogRepository extends JpaRepository<McpDownloadLog, Long> {

    /**
     * "Download All" log rows within a window for a set of MCPs (chart
     * "total downloads" metric). Only the rows are needed — the caller
     * counts them (excluding the MCP creator's own downloads).
     */
    List<McpDownloadLog> findByMcpIdInAndDownloadedAtBetweenAndDownloadAllTrue(
            Collection<Long> mcpIds, Instant start, Instant end);

    /** All download events of a set of MCPs (grid "Downloads" column). */
    List<McpDownloadLog> findByMcpIdIn(Collection<Long> mcpIds);

    /** Download count of one MCP, excluding the creator's own downloads. */
    long countByMcpIdAndUserIdNot(Long mcpId, Long userId);
}
