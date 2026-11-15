package com.furelise.skillmanagement.aitools.repository;

import com.furelise.skillmanagement.aitools.model.AiToolStageMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for ai_tool_stage_mapping.
 */
public interface AiToolStageMappingRepository
        extends JpaRepository<AiToolStageMapping, AiToolStageMapping.AiToolStageMappingKey> {

    List<AiToolStageMapping> findByToolId(Long toolId);

    List<AiToolStageMapping> findByToolIdIn(Collection<Long> toolIds);

    void deleteByToolId(Long toolId);
}
