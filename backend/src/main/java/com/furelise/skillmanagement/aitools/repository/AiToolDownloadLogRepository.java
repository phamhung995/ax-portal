package com.furelise.skillmanagement.aitools.repository;

import com.furelise.skillmanagement.aitools.model.AiToolDownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiToolDownloadLogRepository extends JpaRepository<AiToolDownloadLog, Long> {

    List<AiToolDownloadLog> findByToolIdIn(Collection<Long> toolIds);
}
