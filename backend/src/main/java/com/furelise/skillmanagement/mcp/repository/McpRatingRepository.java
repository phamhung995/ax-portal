package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface McpRatingRepository extends JpaRepository<McpRating, Long> {

    Optional<McpRating> findByMcpIdAndUserId(Long mcpId, Long userId);

    List<McpRating> findByMcpId(Long mcpId);

    @Query("SELECT AVG(r.rating) FROM McpRating r WHERE r.mcpId = :mcpId")
    BigDecimal averageRatingByMcpId(@org.springframework.data.repository.query.Param("mcpId") Long mcpId);

    @Query("SELECT COUNT(r) FROM McpRating r WHERE r.mcpId = :mcpId")
    long countByMcpId(@org.springframework.data.repository.query.Param("mcpId") Long mcpId);

    @Query("SELECT r FROM McpRating r WHERE r.mcpId IN :mcpIds")
    List<McpRating> findByMcpIdIn(@org.springframework.data.repository.query.Param("mcpIds") List<Long> mcpIds);

    /** Per-MCP rating aggregate (avg + count) for a set of MCPs — one GROUP BY instead of loading all rows. */
    @Query("SELECT r.mcpId, AVG(r.rating), COUNT(r) FROM McpRating r WHERE r.mcpId IN :mcpIds GROUP BY r.mcpId")
    List<Object[]> ratingAggByMcpIdIn(@org.springframework.data.repository.query.Param("mcpIds") List<Long> mcpIds);
}
