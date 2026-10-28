package om.mtc.attendance.service;

import om.mtc.attendance.domain.Role;
import om.mtc.attendance.repository.AuditLogRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.MiscDtos.AuditView;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuditQueryService {
    private static final Logger log = LoggerFactory.getLogger(AuditQueryService.class);

    private final AuditLogRepository repo;
    private final SettingsService settings;

    public AuditQueryService(AuditLogRepository repo, SettingsService settings) {
        this.repo = repo;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> search(CurrentUser cu, String action, String username, Instant from, Instant to,
                                          int page, int size) {
        if (!cu.is(Role.COMPANY_AGENT)) {
            throw ApiException.forbidden();
        }
        Instant t = to == null ? Instant.now() : to;
        Instant f = from == null ? t.minus(Duration.ofDays(30)) : from;
        var p = repo.search(blank(action), blank(username) == null ? null : username.trim().toLowerCase(), f, t,
                PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))));
        return PageResponse.of(p.map(AuditView::of));
    }

    /** Nightly purge of audit rows past the retention window (the DB trigger forbids < 365 days). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        int days = Math.max(365, settings.auditRetentionDays());
        int n = repo.deleteOlderThan(Instant.now().minus(Duration.ofDays(days)));
        if (n > 0) {
            log.info("Purged {} audit rows older than {} days", n, days);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
