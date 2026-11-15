package com.furelise.skillmanagement.org.repository;

import com.furelise.skillmanagement.org.model.OrganizationMaster;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Repository for organization master data (Excel import).
 * One person (knox_id) = one row.
 */
public interface OrganizationRepository extends JpaRepository<OrganizationMaster, String>,
        JpaSpecificationExecutor<OrganizationMaster> {

    /** The single org row for a Knox ID (case-insensitive). */
    List<OrganizationMaster> findByKnoxIdIgnoreCase(String knoxId);

    /** Whether the Knox ID has at least one org row with the given employed value. */
    boolean existsByKnoxIdIgnoreCaseAndEmployed(String knoxId, String employed);

    /** Distinct non-blank Part values, ordered. */
    @Query("select distinct o.part from OrganizationMaster o where o.part is not null and o.part <> '' order by o.part")
    List<String> findDistinctPart();

    /** Distinct non-blank Group values, ordered. */
    @Query("select distinct o.groupName from OrganizationMaster o where o.groupName is not null and o.groupName <> '' order by o.groupName")
    List<String> findDistinctGroupName();

    /** Distinct non-blank Center values, ordered. */
    @Query("select distinct o.center from OrganizationMaster o where o.center is not null and o.center <> '' order by o.center")
    List<String> findDistinctCenter();

    /** Distinct non-blank Division values, ordered. */
    @Query("select distinct o.division from OrganizationMaster o where o.division is not null and o.division <> '' order by o.division")
    List<String> findDistinctDivision();

    /** Distinct Knox IDs with at least one employed='Y' row (chart bucketing). */
    @Query("select distinct o.knoxId from OrganizationMaster o where o.employed = 'Y' and o.knoxId is not null")
    List<String> findDistinctKnoxIdWhereEmployedY();

    /** Org rows with the given employed flag (chart bucketing). */
    List<OrganizationMaster> findByEmployed(String employed);

    /** Org rows of one group with the given employed flag (chart part mode).
     *  Matches the legacy in-memory filter: trimmed + case-insensitive. */
    @Query("select o from OrganizationMaster o "
            + "where o.employed = :employed and lower(trim(o.groupName)) = lower(:groupName)")
    List<OrganizationMaster> findByEmployedAndGroupNameTrimmedIgnoreCase(
            @Param("employed") String employed,
            @Param("groupName") String groupName);

    /** Distinct non-blank group names with employed='Y' rows (chart dimensions). */
    @Query("select distinct o.groupName from OrganizationMaster o "
            + "where o.employed = 'Y' and o.groupName is not null and o.groupName <> ''")
    List<String> findDistinctGroupNameWhereEmployedY();

    /** (groupName, part) pairs with employed='Y' and non-blank part (chart dimensions). */
    @Query("select o.groupName, o.part from OrganizationMaster o "
            + "where o.employed = 'Y' and o.part is not null and o.part <> ''")
    List<Object[]> findGroupPartWhereEmployedY();

    /** (groupName, center) pairs with employed='Y' and non-blank group+center. */
    @Query("select o.groupName, o.center from OrganizationMaster o "
            + "where o.employed = 'Y' and o.groupName is not null and o.groupName <> '' "
            + "and o.center is not null and o.center <> ''")
    List<Object[]> findGroupCenterWhereEmployedY();

    /** (groupName, division) pairs with employed='Y' and non-blank group+division. */
    @Query("select o.groupName, o.division from OrganizationMaster o "
            + "where o.employed = 'Y' and o.groupName is not null and o.groupName <> '' "
            + "and o.division is not null and o.division <> ''")
    List<Object[]> findGroupDivisionWhereEmployedY();

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
