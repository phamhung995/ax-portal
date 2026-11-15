package com.furelise.skillmanagement.audit.service.impl;

import com.furelise.skillmanagement.audit.dto.AuditLogItem;
import com.furelise.skillmanagement.audit.dto.AuditLogPage;
import com.furelise.skillmanagement.audit.model.AuditLog;
import com.furelise.skillmanagement.audit.repository.AuditLogRepository;
import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int DETAILS_MAX = 2000;

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User actor, String action, String entityType, String entityId, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .actorId(actor != null ? actor.getId() : null)
                    .actorName(actor != null ? actor.getName() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(truncate(details))
                    .ipAddress(currentIp())
                    .build());
        } catch (Exception e) {
            // Never break the business operation because of audit logging.
            log.warn("Audit log write failed ({} {} {}): {}", action, entityType, entityId, e.getMessage());
        }
    }

    @Override
    public void log(String action, String entityType, String entityId, String details) {
        log(currentPrincipal(), action, entityType, entityId, details);
    }

    /** The authenticated user from the SecurityContext (null when anonymous). */
    private static User currentPrincipal() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User u) {
                return u;
            }
        } catch (Exception ignored) {
            // fall through to null
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogPage page(int page, int pageSize, String actorName, String entityType, String action) {
        int p = page < 1 ? 1 : page;
        int sz = pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (actorName != null && !actorName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorName")),
                        "%" + actorName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim().toUpperCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = auditLogRepository.findAll(spec,
                PageRequest.of(p - 1, sz, Sort.by(Sort.Direction.DESC, "id")));
        List<AuditLogItem> items = result.getContent().stream()
                .map(a -> new AuditLogItem(a.getId(), a.getActorId(), a.getActorName(), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt()))
                .toList();
        return new AuditLogPage(result.getTotalElements(), p, sz, items);
    }

    private static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= DETAILS_MAX ? value : value.substring(0, DETAILS_MAX);
    }
}
