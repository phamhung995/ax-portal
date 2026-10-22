package com.skillmanagement.skillrequest.repository;

import com.skillmanagement.skillrequest.model.SkillRequestFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for skill_request_files.
 * All files are visible to every authenticated user (same as the request).
 */
public interface SkillRequestFileRepository extends JpaRepository<SkillRequestFile, Long> {

    List<SkillRequestFile> findByRequestIdIn(List<Long> requestIds);

    List<SkillRequestFile> findByRequestId(Long requestId);
}
