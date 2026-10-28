package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.*;
import om.mtc.attendance.repository.*;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.ReportDtos.*;
import om.mtc.attendance.web.dto.SessionDtos.Counts;
import om.mtc.attendance.web.dto.SessionDtos.SessionSummary;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Daily and monthly statistics. Definitions:
 * attendance rate = (present + late) / (records - excused);
 * discipline rate = present / (records - excused)  (on time, without lateness).
 */
@Service
public class ReportService {
    private final AttendanceRecordRepository records;
    private final AttendanceSessionRepository sessions;
    private final PlatoonRepository platoons;
    private final AttendanceService attendance;
    private final AccessPolicy access;
    private final AppProperties props;

    public ReportService(AttendanceRecordRepository records, AttendanceSessionRepository sessions,
                         PlatoonRepository platoons, AttendanceService attendance, AccessPolicy access,
                         AppProperties props) {
        this.records = records;
        this.sessions = sessions;
        this.platoons = platoons;
        this.attendance = attendance;
        this.access = access;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public MonthlyReport monthly(CurrentUser cu, YearMonth month, Long platoonId) {
        Scope sc = scope(cu, platoonId);
        YearMonth m = month == null ? YearMonth.now(props.zoneId()) : month;
        List<AttendanceRecord> current = sc.ids.isEmpty() ? List.of()
                : records.findForReport(sc.ids, m.atDay(1), m.atEndOfMonth());
        YearMonth prev = m.minusMonths(1);
        List<AttendanceRecord> previous = sc.ids.isEmpty() ? List.of()
                : records.findForReport(sc.ids, prev.atDay(1), prev.atEndOfMonth());

        Map<Long, List<AttendanceRecord>> bySoldier = new LinkedHashMap<>();
        for (AttendanceRecord r : current) {
            bySoldier.computeIfAbsent(r.getSoldier().getId(), k -> new ArrayList<>()).add(r);
        }
        List<SoldierStats> rows = new ArrayList<>();
        for (List<AttendanceRecord> list : bySoldier.values()) {
            Soldier s = list.get(0).getSoldier();
            int[] c = tally(list);
            Map<AttendanceMethod, Integer> methods = new EnumMap<>(AttendanceMethod.class);
            list.forEach(r -> methods.merge(r.getMethod(), 1, Integer::sum));
            AttendanceMethod main = methods.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            rows.add(new SoldierStats(s.getId(), s.getMilitaryNumber(), s.getFullName(), s.getMilitaryRank(),
                    list.get(0).getSession().getPlatoon().getName(), list.size(), c[0], c[1], c[2], c[3],
                    rate(c[0] + c[1], list.size() - c[2]), main));
        }
        rows.sort(Comparator.comparing(SoldierStats::fullName));
        return new MonthlyReport(m.toString(), platoonId, sc.name, totals(current), totals(previous), rows);
    }

    @Transactional(readOnly = true)
    public DailyReport daily(CurrentUser cu, LocalDate date, Long platoonId) {
        Scope sc = scope(cu, platoonId);
        LocalDate d = date == null ? LocalDate.now(props.zoneId()) : date;
        List<AttendanceSession> list = sc.ids.isEmpty() ? List.of() : sessions.findInRange(sc.ids, d, d);
        Map<Long, Counts> counts = attendance.countsFor(list);
        List<SessionSummary> summaries = list.stream().map(s -> attendance.summary(s, counts.get(s.getId())))
                .toList();
        List<AttendanceRecord> recs = sc.ids.isEmpty() ? List.of() : records.findForReport(sc.ids, d, d);
        List<DailyAbsence> exceptions = recs.stream()
                .filter(r -> r.getStatus() != AttendanceStatus.PRESENT)
                .sorted(Comparator.comparing((AttendanceRecord r) -> r.getStatus().ordinal())
                        .thenComparing(r -> r.getSoldier().getFullName()))
                .map(r -> new DailyAbsence(r.getSoldier().getFullName(), r.getSoldier().getMilitaryNumber(),
                        r.getStatus().name(), r.getNote(),
                        r.getSession().getPlatoon().getName() + " — " + r.getSession().getSessionType().name()))
                .toList();
        return new DailyReport(d, platoonId, sc.name, totals(recs), summaries, exceptions);
    }

    private record Scope(List<Long> ids, String name) {
    }

    private Scope scope(CurrentUser cu, Long platoonId) {
        access.requireReports(cu, platoonId);
        if (platoonId != null) {
            access.requireRead(cu, platoonId);
            Platoon p = platoons.findById(platoonId).orElseThrow(() -> ApiException.notFound("الفصيل"));
            return new Scope(List.of(platoonId), p.getName());
        }
        List<Long> ids = access.readablePlatoonIds(cu);
        String name = ids.size() == 1 ? platoons.findById(ids.get(0)).map(Platoon::getName).orElse(null)
                : "جميع الفصائل";
        return new Scope(ids, name);
    }

    /** [present, late, excused, absent] */
    private static int[] tally(List<AttendanceRecord> list) {
        int[] c = new int[4];
        for (AttendanceRecord r : list) {
            switch (r.getStatus()) {
                case PRESENT -> c[0]++;
                case LATE -> c[1]++;
                case EXCUSED -> c[2]++;
                case ABSENT -> c[3]++;
            }
        }
        return c;
    }

    private static Totals totals(List<AttendanceRecord> list) {
        int[] c = tally(list);
        long sessionCount = list.stream().map(r -> r.getSession().getId()).distinct().count();
        int denom = list.size() - c[2];
        return new Totals((int) sessionCount, list.size(), c[0], c[1], c[2], c[3],
                rate(c[0] + c[1], denom), rate(c[0], denom));
    }

    private static double rate(int num, int denom) {
        return denom <= 0 ? 0 : Math.round(1000.0 * num / denom) / 10.0;
    }
}
