package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.SystemImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemImportJobRepository extends JpaRepository<SystemImportJob, Long> {

    /** The newest job with the given status (for PENDING: the only one that runs). */
    Optional<SystemImportJob> findTopByStatusOrderByIdDesc(String status);

    /** All jobs with the given status (older PENDING ones are superseded on new upload). */
    List<SystemImportJob> findByStatus(String status);

    /** The newest finished job (SUCCESS or FAILED) for the "last import" line. */
    Optional<SystemImportJob> findTopByStatusInOrderByIdDesc(List<String> statuses);
}
