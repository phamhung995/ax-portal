package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.HrSystemAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HrSystemAssignmentRepository extends JpaRepository<HrSystemAssignment, Long>,
        JpaSpecificationExecutor<HrSystemAssignment> {

    /** Distinct part values for the Part filter dropdown (ordered). */
    @Query("select distinct h.part from HrSystemAssignment h where h.part is not null and h.part <> '' order by h.part")
    List<String> findDistinctPart();

    /**
     * All person/system assignments with the 12 month columns, for the
     * caller to decide which systems are "PIC" (any month column from jan
     * up to the current month > 0).
     */
    @org.springframework.data.jpa.repository.Query(value =
            "select knox_id, system_key, jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, dec "
            + "from hr_system_assignment",
            nativeQuery = true)
    List<Object[]> findAllKnoxSystemMonths();
}
