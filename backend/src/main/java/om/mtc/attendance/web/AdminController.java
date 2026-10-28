package om.mtc.attendance.web;

import jakarta.validation.Valid;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.AuditQueryService;
import om.mtc.attendance.service.SettingsService;
import om.mtc.attendance.web.dto.MiscDtos.AuditView;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.MiscDtos.SettingsUpdate;
import om.mtc.attendance.web.dto.MiscDtos.SettingsView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final AuditQueryService auditQuery;
    private final SettingsService settings;

    public AdminController(AuditQueryService auditQuery, SettingsService settings) {
        this.auditQuery = auditQuery;
        this.settings = settings;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('COMPANY_AGENT')")
    public PageResponse<AuditView> audit(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return auditQuery.search(SecurityUtils.currentUser(), action, username, from, to, page, size);
    }

    /** Readable by every signed-in user (the UI shows the active threshold); writable by the agent only. */
    @GetMapping("/settings")
    public SettingsView getSettings() {
        return settings.view();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('COMPANY_AGENT')")
    public SettingsView updateSettings(@Valid @RequestBody SettingsUpdate u) {
        return settings.update(u);
    }
}
