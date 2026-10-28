package om.mtc.attendance.service;

import om.mtc.attendance.domain.SystemSetting;
import om.mtc.attendance.repository.SystemSettingRepository;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.web.dto.MiscDtos.SettingsUpdate;
import om.mtc.attendance.web.dto.MiscDtos.SettingsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tunable system settings stored in the database (threshold, grace period, retention...). */
@Service
public class SettingsService {
    public static final String THRESHOLD = "recognition.threshold";
    public static final String MIN_DET = "recognition.minDetScore";
    public static final String ENROLL_MIN_DET = "enrollment.minDetScore";
    public static final String ENROLL_MAX = "enrollment.maxFaces";
    public static final String ENROLL_DUP = "enrollment.duplicateThreshold";
    public static final String LATE_GRACE = "attendance.lateGraceMinutes";
    public static final String AUDIT_RETENTION = "audit.retentionDays";

    private static final Map<String, String> DEFAULTS = Map.of(
            THRESHOLD, "0.45", MIN_DET, "0.60", ENROLL_MIN_DET, "0.70", ENROLL_MAX, "5",
            ENROLL_DUP, "0.60", LATE_GRACE, "10", AUDIT_RETENTION, "730");

    private final SystemSettingRepository repo;
    private final AuditService audit;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SystemSettingRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    private String raw(String key) {
        return cache.computeIfAbsent(key, k -> repo.findById(k).map(SystemSetting::getValue)
                .orElse(DEFAULTS.get(k)));
    }

    public double recognitionThreshold() {
        return Double.parseDouble(raw(THRESHOLD));
    }

    public double recognitionMinDetScore() {
        return Double.parseDouble(raw(MIN_DET));
    }

    public double enrollmentMinDetScore() {
        return Double.parseDouble(raw(ENROLL_MIN_DET));
    }

    public int enrollmentMaxFaces() {
        return Integer.parseInt(raw(ENROLL_MAX));
    }

    public double enrollmentDuplicateThreshold() {
        return Double.parseDouble(raw(ENROLL_DUP));
    }

    public int lateGraceMinutes() {
        return Integer.parseInt(raw(LATE_GRACE));
    }

    public int auditRetentionDays() {
        return Integer.parseInt(raw(AUDIT_RETENTION));
    }

    public SettingsView view() {
        return new SettingsView(recognitionThreshold(), recognitionMinDetScore(), enrollmentMinDetScore(),
                enrollmentMaxFaces(), enrollmentDuplicateThreshold(), lateGraceMinutes(), auditRetentionDays());
    }

    @Transactional
    public SettingsView update(SettingsUpdate u) {
        SettingsView before = view();
        put(THRESHOLD, String.valueOf(u.recognitionThreshold()));
        put(MIN_DET, String.valueOf(u.recognitionMinDetScore()));
        put(ENROLL_MIN_DET, String.valueOf(u.enrollmentMinDetScore()));
        put(ENROLL_MAX, String.valueOf(u.enrollmentMaxFaces()));
        put(ENROLL_DUP, String.valueOf(u.enrollmentDuplicateThreshold()));
        put(LATE_GRACE, String.valueOf(u.lateGraceMinutes()));
        put(AUDIT_RETENTION, String.valueOf(u.auditRetentionDays()));
        cache.clear();
        SettingsView after = view();
        audit.log("SETTINGS_UPDATED", "SETTINGS", null, "before=" + before + " after=" + after);
        return after;
    }

    private void put(String key, String value) {
        SystemSetting s = repo.findById(key).orElseGet(() -> {
            SystemSetting n = new SystemSetting();
            n.setKey(key);
            return n;
        });
        s.setValue(value);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(SecurityUtils.currentUserOptional().map(c -> c.id()).orElse(null));
        repo.save(s);
    }

    /** For tests and admin tooling. */
    public void evictCache() {
        cache.clear();
    }
}
