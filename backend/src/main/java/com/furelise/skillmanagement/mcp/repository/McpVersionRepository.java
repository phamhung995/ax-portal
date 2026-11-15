package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface McpVersionRepository extends JpaRepository<McpVersion, Long> {

    List<McpVersion> findByMcpIdOrderByVersionDesc(Long mcpId);

    Optional<McpVersion> findByMcpIdAndVersion(Long mcpId, Integer version);

    List<McpVersion> findByMcpIdInOrderByVersionDesc(Collection<Long> mcpIds);
}
