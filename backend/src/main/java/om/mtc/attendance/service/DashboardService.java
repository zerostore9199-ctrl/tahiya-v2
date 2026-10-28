package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.*;
import om.mtc.attendance.repository.*;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.DashboardDtos.*;
import om.mtc.attendance.web.dto.SessionDtos.Counts;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class DashboardService {
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final SoldierRepository soldiers;
    private final PlatoonRepository platoons;
    private final AppUserRepository users;
    private final AuditLogRepository auditLogs;
    private final AttendanceService attendance;
    private final AccessPolicy access;
    private final AppProperties props;

    public DashboardService(AttendanceSessionRepository sessions, AttendanceRecordRepository records,
                            SoldierRepository soldiers, PlatoonRepository platoons, AppUserRepository users,
                            AuditLogRepository auditLogs, AttendanceService attendance, AccessPolicy access,
                            AppProperties props) {
        this.sessions = sessions;
        this.records = records;
        this.soldiers = soldiers;
        this.platoons = platoons;
        this.users = users;
        this.auditLogs = auditLogs;
        this.attendance = attendance;
        this.access = access;
        this.props = props;
    }

    /** Live view of today: one row per visible platoon with its latest session of the day. */
    @Transactional(readOnly = true)
    public LiveDashboard live(CurrentUser cu) {
        LocalDate today = LocalDate.now(props.zoneId());
        List<Long> scope = access.readablePlatoonIds(cu);
        Map<Long, Long> strength = new HashMap<>();
        for (Object[] row : soldiers.countActiveByPlatoon()) {
            strength.put((Long) row[0], (Long) row[1]);
        }

        List<AttendanceSession> todays = scope.isEmpty() ? List.of() : sessions.findInRange(scope, today, today);
        Map<Long, AttendanceSession> latest = new HashMap<>();
        for (AttendanceSession s : todays) { // ordered by openedAt asc → last one wins
            latest.put(s.getPlatoonId(), s);
        }
        Map<Long, Counts> counts = attendance.countsFor(new ArrayList<>(latest.values()));

        long totalSoldiers = 0;
        int present = 0, late = 0, absent = 0, excused = 0;
        List<PlatoonLive> rows = new ArrayList<>();
        for (Platoon p : platoons.findByActiveTrueOrderByNameAsc()) {
            if (!scope.contains(p.getId())) {
                continue;
            }
            long active = strength.getOrDefault(p.getId(), 0L);
            totalSoldiers += active;
            String senior = users.findByRoleAndPlatoon_IdAndEnabledTrue(Role.SENIOR, p.getId()).stream()
                    .map(AppUser::getFullName).findFirst().orElse(null);
            AttendanceSession s = latest.get(p.getId());
            Counts c = s == null ? null : counts.get(s.getId());
            if (c != null) {
                present += c.present();
                late += c.late();
                absent += c.absent();
                excused += c.excused();
            }
            rows.add(new PlatoonLive(p.getId(), p.getName(), senior, active,
                    s == null ? null : s.getId(), s == null ? null : s.getSessionType().name(),
                    s == null ? null : s.getStatus(), s == null ? null : s.getOpenedAt(), c));
        }
        int denom = (int) totalSoldiers - excused;
        double rate = denom <= 0 ? 0 : Math.round(1000.0 * (present + late) / denom) / 10.0;
        Kpis kpis = new Kpis(totalSoldiers, present, late, absent, excused, rate);

        List<PendingApproval> pending = new ArrayList<>();
        if (!cu.is(Role.SENIOR) && !scope.isEmpty()) {
            List<AttendanceSession> closed = sessions.findPendingApproval(scope);
            Map<Long, Counts> pc = attendance.countsFor(closed);
            for (AttendanceSession s : closed) {
                Counts c = pc.get(s.getId());
                pending.add(new PendingApproval(s.getId(), s.getPlatoon().getName(), s.getSessionType().name(),
                        s.getSessionDate(), s.getClosedAt(), c.absent(), c.late()));
            }
        }

        List<AuditEntry> activity = List.of();
        if (cu.is(Role.COMPANY_AGENT)) {
            Instant now = Instant.now();
            activity = auditLogs.search(null, null, now.minusSeconds(7 * 86400), now, PageRequest.of(0, 8))
                    .map(a -> new AuditEntry(a.getOccurredAt(), a.getUsername(), a.getAction(), a.getDetails()))
                    .getContent();
        }
        return new LiveDashboard(today, kpis, rows, pending, activity);
    }

    /** Daily attendance rate for the last N days (closed and approved sessions only). */
    @Transactional(readOnly = true)
    public List<TrendPoint> trend(CurrentUser cu, Long platoonId, int days) {
        int n = Math.max(1, Math.min(90, days));
        List<Long> scope;
        if (platoonId != null) {
            access.requireRead(cu, platoonId);
            scope = List.of(platoonId);
        } else {
            scope = access.readablePlatoonIds(cu);
        }
        LocalDate today = LocalDate.now(props.zoneId());
        LocalDate from = today.minusDays(n - 1L);
        Map<LocalDate, int[]> agg = new TreeMap<>(); // [present+late, excused, total]
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            agg.put(d, new int[3]);
        }
        if (!scope.isEmpty()) {
            for (AttendanceRecord r : records.findForReport(scope, from, today)) {
                int[] a = agg.get(r.getSession().getSessionDate());
                if (a == null) {
                    continue;
                }
                if (r.getStatus() == AttendanceStatus.PRESENT || r.getStatus() == AttendanceStatus.LATE) {
                    a[0]++;
                } else if (r.getStatus() == AttendanceStatus.EXCUSED) {
                    a[1]++;
                }
                a[2]++;
            }
        }
        List<TrendPoint> out = new ArrayList<>();
        agg.forEach((d, a) -> {
            int denom = a[2] - a[1];
            double rate = denom <= 0 ? 0 : Math.round(1000.0 * a[0] / denom) / 10.0;
            out.add(new TrendPoint(d, rate, a[2]));
        });
        return out;
    }
}
