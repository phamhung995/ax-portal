package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.user.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface McpMasterRepository extends JpaRepository<McpMaster, Long>, JpaSpecificationExecutor<McpMaster> {

    Optional<McpMaster> findByServerKey(String serverKey);

    List<McpMaster> findByDeletedYn(String deletedYn);

    List<McpMaster> findByIdIn(Collection<Long> ids);

    boolean existsByMcpNameIgnoreCaseAndDeletedYnAndCreatedBy(String mcpName, String deletedYn, Long createdBy);

    boolean existsByMcpNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(String mcpName, Long id, String deletedYn, Long createdBy);

    long countByStatusAndDeletedYn(String status, String deletedYn);

    /**
     * Visibility Specification (PUBLIC only — no scope):
     * deleted_yn = 'N' AND (creator OR admin OR APPROVED OR
     * (PENDING_APPROVAL + pendingMetadata != null — version update still live)).
     */
    static Specification<McpMaster> visibleTo(User viewer) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deletedYn"), "N"));

            boolean isAdmin = viewer.getRole() != null
                    && "ADMIN_USER".equals(viewer.getRole().getName());
            Long viewerId = viewer.getId();

            if (!isAdmin) {
                predicates.add(cb.or(
                        cb.equal(root.get("createdBy"), viewerId),
                        cb.equal(root.get("status"), McpMaster.STATUS_APPROVED),
                        cb.and(
                                cb.equal(root.get("status"), McpMaster.STATUS_PENDING_APPROVAL),
                                cb.isNotNull(root.get("pendingMetadata")))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Status filter matching the grid's <b>display</b> status:
     * - admin: real status
     * - non-admin: own MCPs real status; others' PENDING_APPROVAL + pendingMetadata
     *   displays as APPROVED.
     */
    static Specification<McpMaster> statusFilter(User viewer, Collection<String> statuses) {
        return (root, query, cb) -> {
            boolean isAdmin = viewer.getRole() != null
                    && "ADMIN_USER".equals(viewer.getRole().getName());
            if (isAdmin) {
                return root.get("status").in(statuses);
            }
            Long viewerId = viewer.getId();
            Predicate own = cb.and(
                    cb.equal(root.get("createdBy"), viewerId),
                    root.get("status").in(statuses));
            Predicate othersReal = cb.and(
                    cb.notEqual(root.get("createdBy"), viewerId),
                    root.get("status").in(statuses),
                    cb.or(
                            cb.notEqual(root.get("status"), McpMaster.STATUS_PENDING_APPROVAL),
                            cb.isNull(root.get("pendingMetadata"))));
            List<Predicate> branches = new ArrayList<>(List.of(own, othersReal));
            if (statuses.contains(McpMaster.STATUS_APPROVED)) {
                branches.add(cb.and(
                        cb.notEqual(root.get("createdBy"), viewerId),
                        cb.equal(root.get("status"), McpMaster.STATUS_PENDING_APPROVAL),
                        cb.isNotNull(root.get("pendingMetadata"))));
            }
            return cb.or(branches.toArray(new Predicate[0]));
        };
    }
}
