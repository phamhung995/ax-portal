package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.SystemMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SystemMasterRepository
        extends JpaRepository<SystemMaster, Long>, JpaSpecificationExecutor<SystemMaster> {

    /** Distinct GDC Part values (search filter options). */
    @Query("SELECT DISTINCT s.gdcPart FROM SystemMaster s WHERE s.gdcPart IS NOT NULL ORDER BY s.gdcPart")
    List<String> findDistinctGdcPart();

    /** Systems by a set of keys (PROJECT scope: resolve PIC systems to projects). */
    List<SystemMaster> findBySystemKeyIn(Collection<String> systemKeys);

    /** One system by its key (skill detail: resolve the system name). */
    SystemMaster findBySystemKey(String systemKey);

    /** Bulk delete all rows (full-replace import). */
    @Modifying
    @Query("DELETE FROM SystemMaster s")
    void deleteAllInBatch();
}
