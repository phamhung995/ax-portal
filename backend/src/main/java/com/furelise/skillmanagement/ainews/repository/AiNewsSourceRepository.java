package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiNewsSourceRepository extends JpaRepository<AiNewsSource, Long> {

    List<AiNewsSource> findByStatus(String status);

    Optional<AiNewsSource> findByMailbox(String mailbox);
}
