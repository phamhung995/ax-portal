package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.SkillMcpDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillMcpDependencyRepository extends JpaRepository<SkillMcpDependency, Long> {

    List<SkillMcpDependency> findByMcpId(Long mcpId);

    List<SkillMcpDependency> findBySkillId(Long skillId);

    boolean existsByMcpId(Long mcpId);

    /**
     * Bulk (JPQL) delete — executes immediately on the DB. The derived
     * {@code deleteBySkillId} is deferred to flush, which collides with the
     * IDENTITY insert in {@code saveDependencies} (unique skill_id+mcp_id
     * violated before the old rows are gone).
     */
    @Modifying
    @Query("delete from SkillMcpDependency d where d.skillId = :skillId")
    void bulkDeleteBySkillId(@Param("skillId") Long skillId);
}
