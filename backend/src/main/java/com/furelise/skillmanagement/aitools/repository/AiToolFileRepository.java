package com.furelise.skillmanagement.aitools.repository;

import com.furelise.skillmanagement.aitools.model.AiToolFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiToolFileRepository extends JpaRepository<AiToolFile, Long> {

    List<AiToolFile> findByToolId(Long toolId);

    List<AiToolFile> findByToolIdIn(java.util.Collection<Long> toolIds);

    void deleteByToolId(Long toolId);
}
