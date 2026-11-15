package com.furelise.skillmanagement.aitools.repository;

import com.furelise.skillmanagement.aitools.model.AiToolCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for ai_tool_category_mapping.
 */
public interface AiToolCategoryMappingRepository
        extends JpaRepository<AiToolCategoryMapping, AiToolCategoryMapping.AiToolCategoryMappingKey> {

    List<AiToolCategoryMapping> findByToolId(Long toolId);

    List<AiToolCategoryMapping> findByToolIdIn(Collection<Long> toolIds);

    void deleteByToolId(Long toolId);
}
