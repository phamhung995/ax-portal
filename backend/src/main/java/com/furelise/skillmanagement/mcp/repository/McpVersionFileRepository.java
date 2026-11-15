package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpVersionFileRepository extends JpaRepository<McpVersionFile, Long> {

    List<McpVersionFile> findByMcpVersionId(Long mcpVersionId);
}
