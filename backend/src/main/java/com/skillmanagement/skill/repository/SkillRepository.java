package com.skillmanagement.skill.repository;

import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.user.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.time.Instant;

/**
 * Repository for skills_master.
 * Visibility rules (status + scope) are enforced via Specifications —
 * see {@link #visibleTo}.
 */
public interface SkillRepository extends JpaRepository<Skill, Long>, JpaSpecificationExecutor<Skill> {

    /** Case-insensitive name uniqueness check for one creator (active skills only). */
    boolean existsByNameIgnoreCaseAndDeletedYnAndCreatedBy(String name, String deletedYn, Long createdBy);

    /** Case-insensitive name check for one creator, excluding one skill (edit duplicate check, active only). */
    boolean existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(String name, Long id, String deletedYn, Long createdBy);

    List<Skill> findByCreatedBy(Long createdBy);

    List<Skill> findByIdIn(Collection<Long> ids);

    /** My skills (all statuses, not deleted), newest first. */
    List<Skill> findByCreatedByAndDeletedYnOrderByCreatedAtDesc(Long createdBy, String deletedYn);

    /** Count of my skills in a given status (not deleted). */
    long countByCreatedByAndDeletedYnAndApprovalStatus(Long createdBy, String deletedYn, String approvalStatus);

    /** Sum of view counts of my not-deleted skills. */
    @Query("SELECT COALESCE(SUM(s.viewCount), 0) FROM Skill s WHERE s.createdBy = :createdBy AND s.deletedYn = 'N'")
    long sumViewCountByCreatedBy(@Param("createdBy") Long createdBy);

    /** Count of PENDING_APPROVAL skills (admin banner). */
    long countByApprovalStatusAndDeletedYn(String approvalStatus, String deletedYn);

    /**
     * Daily updated-at counts for a creator's skills since a point in time
     * (activity chart). Returns rows of [LocalDate day, long count], bucketed
     * in the given zone (must match the zone the caller uses to build its
     * day indices).
     */
    @Query(value = "SELECT (s.updated_at AT TIME ZONE :tz)::date AS d, COUNT(*) "
            + "FROM skills_master s "
            + "WHERE s.created_by = :userId AND s.deleted_yn = 'N' AND s.updated_at >= :since "
            + "GROUP BY 1", nativeQuery = true)
    List<Object[]> countUpdatedByDaySince(
            @Param("userId") Long userId,
            @Param("since") Instant since,
            @Param("tz") String tz);

    /**
     * Visibility Specification: a skill is visible to the viewer when
     * deleted_yn = 'N' AND the status rule passes AND the scope rule passes
     * (admin bypasses the scope rule).
     *
     * <p>Scope rule (non-admin):
     * <ul>
     *   <li>PRIVATE -> viewer is the creator</li>
     *   <li>PUBLIC  -> everyone</li>
     *   <li>PART    -> the creator has an org row (employed='Y') whose Part
     *                  matches one of the viewer's Parts</li>
     *   <li>GROUP   -> same for Group</li>
     * </ul>
     * The creator's org rows are resolved via a subquery joining
     * organization_master to "user" on knox_id, restricted to
     * user.id = skill.created_by.
     *
     * @param viewer       the logged-in user
     * @param viewerParts  distinct Part values of the viewer (org rows Employed=Y)
     * @param viewerGroups distinct Group values of the viewer (org rows Employed=Y)
     */
    static Specification<Skill> visibleTo(User viewer,
                                          Collection<String> viewerParts,
                                          Collection<String> viewerGroups) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deletedYn"), "N"));

            boolean isAdmin = viewer.getRole() != null
                    && "ADMIN_USER".equals(viewer.getRole().getName());
            Long viewerId = viewer.getId();

            if (!isAdmin) {
                // --- Status rule: own skills all statuses; others' APPROVED
                //     only — plus skills with a pending VERSION UPDATE, which
                //     still serve the approved version to everyone else. ---
                predicates.add(cb.or(
                        cb.equal(root.get("createdBy"), viewerId),
                        cb.equal(root.get("approvalStatus"), Skill.STATUS_APPROVED),
                        cb.and(
                                cb.equal(root.get("approvalStatus"), Skill.STATUS_PENDING_APPROVAL),
                                cb.isNotNull(root.get("pendingMetadata")))));

                // --- Scope rule ---
                Predicate privateOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_PRIVATE),
                        cb.equal(root.get("createdBy"), viewerId));
                Predicate publicOk = cb.equal(root.get("publicScope"), Skill.SCOPE_PUBLIC);
                Predicate partOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_PART),
                        orgOverlap(root, query, cb, "part", viewerParts));
                Predicate groupOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_GROUP),
                        orgOverlap(root, query, cb, "groupName", viewerGroups));
                predicates.add(cb.or(privateOk, publicOk, partOk, groupOk));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * EXISTS (SELECT 1 FROM organization_master o, "user" u
     *         WHERE u.id = skill.created_by
     *           AND u.knox_id = o.knox_id
     *           AND o.employed = 'Y'
     *           AND LOWER(o.{orgColumn}) IN (viewer values)).
     */
    static Predicate orgOverlap(Root<Skill> root,
                                jakarta.persistence.criteria.CriteriaQuery<?> query,
                                CriteriaBuilder cb,
                                String orgColumn,
                                Collection<String> viewerValues) {
        if (viewerValues == null || viewerValues.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<OrganizationMaster> org = sq.from(OrganizationMaster.class);
        Root<User> creatorUser = sq.from(User.class);
        List<String> lowerValues =
                viewerValues.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
        sq.select(cb.literal(1)).where(
                cb.equal(creatorUser.get("id"), root.get("createdBy")),
                cb.equal(creatorUser.get("knoxId"), org.get("knoxId")),
                cb.equal(org.get("employed"), "Y"),
                cb.lower(org.get(orgColumn)).in(lowerValues));
        return cb.exists(sq);
    }
}
