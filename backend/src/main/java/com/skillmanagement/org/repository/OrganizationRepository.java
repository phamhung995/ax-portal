package com.skillmanagement.org.repository;

import com.skillmanagement.org.model.OrganizationMaster;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Repository for organization master data (Excel import).
 * Composite PK: (knox_id, employee_number, part, group_name).
 */
public interface OrganizationRepository extends JpaRepository<OrganizationMaster, OrganizationMaster.OrganizationMasterId>,
        JpaSpecificationExecutor<OrganizationMaster> {

    /** All org rows for a Knox ID (case-insensitive). */
    List<OrganizationMaster> findByKnoxIdIgnoreCase(String knoxId);

    /** Whether the Knox ID has at least one org row with the given employed value. */
    boolean existsByKnoxIdIgnoreCaseAndEmployed(String knoxId, String employed);

    /** Whether an org row exists for the given Knox ID (case-insensitive) and employee number. */
    boolean existsByKnoxIdIgnoreCaseAndEmployeeNumber(String knoxId, String employeeNumber);

    /** Distinct non-blank Part values, ordered. */
    @Query("select distinct o.part from OrganizationMaster o where o.part is not null and o.part <> '' order by o.part")
    List<String> findDistinctPart();

    /** Distinct non-blank Group values, ordered. */
    @Query("select distinct o.groupName from OrganizationMaster o where o.groupName is not null and o.groupName <> '' order by o.groupName")
    List<String> findDistinctGroupName();

    /** Specification: org rows whose Knox ID is in the given set (case-insensitive). */
    static Specification<OrganizationMaster> knoxIdInIgnoreCase(Collection<String> knoxIds) {
        return (root, query, cb) -> {
            if (knoxIds == null || knoxIds.isEmpty()) {
                return cb.disjunction();
            }
            Predicate p = root.get("knoxId").in(knoxIds);
            Predicate pl = cb.lower(root.get("knoxId")).in(
                    knoxIds.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
            return cb.or(p, pl);
        };
    }
}
