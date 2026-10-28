package om.mtc.attendance.service;

import om.mtc.attendance.domain.AuditLog;
import om.mtc.attendance.repository.AuditLogRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only audit trail (سجل العمليات).
 * Details must never contain passwords, tokens, embeddings or images.
 */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Joins the caller's transaction: if the business operation rolls back, so does its audit row. */
    @Transactional
    public void log(String action, String entityType, Object entityId, String details) {
        CurrentUser cu = SecurityUtils.currentUserOptional().orElse(null);
        save(cu == null ? null : cu.id(), cu == null ? null : cu.username(), action, entityType, entityId, details,
                true);
    }

    /** Independent transaction: kept even when the caller fails (used for login failures, denials). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(Long userId, String username, String action, String entityType, Object entityId,
                               String details, boolean success) {
        save(userId, username, action, entityType, entityId, details, success);
    }

    private void save(Long userId, String username, String action, String entityType, Object entityId,
                      String details, boolean success) {
        AuditLog a = new AuditLog();
        a.setUserId(userId);
        a.setUsername(truncate(username, 50));
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : truncate(String.valueOf(entityId), 40));
        a.setDetails(truncate(details, 1000));
        a.setIpAddress(truncate(SecurityUtils.clientIp(), 64));
        a.setSuccess(success);
        repo.save(a);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String clean = s.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
