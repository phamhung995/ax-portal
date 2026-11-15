package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.SubSystemMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SubSystemMasterRepository extends JpaRepository<SubSystemMaster, Long> {

    List<SubSystemMaster> findBySystemKeyIn(Collection<String> systemKeys);

    /** One sub system by parent key + code (skill detail: resolve the name). */
    SubSystemMaster findBySystemKeyAndSubSystemCode(String systemKey, String subSystemCode);

    /** Bulk delete all rows (full-replace import). */
    @Modifying
    @Query("DELETE FROM SubSystemMaster s")
    void deleteAllInBatch();
}
