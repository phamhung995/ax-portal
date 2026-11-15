package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.OrgImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgImportJobRepository extends JpaRepository<OrgImportJob, Long> {

    /** The newest job with the given status (for PENDING: the only one that runs). */
    Optional<OrgImportJob> findTopByStatusOrderByIdDesc(String status);

    /** All jobs with the given status (older PENDING ones are superseded on new upload). */
    List<OrgImportJob> findByStatus(String status);

    /** The newest finished job (SUCCESS or FAILED) for the "last import" line. */
    Optional<OrgImportJob> findTopByStatusInOrderByIdDesc(List<String> statuses);
}
