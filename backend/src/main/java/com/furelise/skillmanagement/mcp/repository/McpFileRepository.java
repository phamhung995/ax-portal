package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface McpFileRepository extends JpaRepository<McpFile, Long> {

    List<McpFile> findByMcpIdIn(Collection<Long> mcpIds);

    /** Live files of an MCP (excludes pending version-update uploads). */
    List<McpFile> findByMcpIdAndPendingYn(Long mcpId, String pendingYn);
}
