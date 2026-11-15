package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface McpCommentRepository extends JpaRepository<McpComment, Long> {

    Page<McpComment> findByMcpIdAndDeletedAtIsNull(Long mcpId, Pageable pageable);

    List<McpComment> findByMcpIdAndDeletedAtIsNull(Long mcpId);

    @Query("SELECT COUNT(c) FROM McpComment c WHERE c.mcpId = :mcpId AND c.deletedAt IS NULL")
    long countByMcpIdAndDeletedAtIsNull(@org.springframework.data.repository.query.Param("mcpId") Long mcpId);
}
