package om.mtc.attendance.web.dto;

import om.mtc.attendance.domain.AttendanceMethod;

import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record SoldierStats(Long soldierId, String militaryNumber, String fullName, String militaryRank,
                               String platoonName, int sessions, int present, int late, int excused, int absent,
                               double attendanceRate, AttendanceMethod mainMethod) {
    }

    public record Totals(int sessions, int records, int present, int late, int excused, int absent,
                         double attendanceRate, double disciplineRate) {
    }

    public record MonthlyReport(String month, Long platoonId, String platoonName, Totals totals,
                                Totals previousMonth, List<SoldierStats> soldiers) {
    }

    public record DailyAbsence(String fullName, String militaryNumber, String status, String note,
                               String sessionLabel) {
    }

    public record DailyReport(LocalDate date, Long platoonId, String platoonName, Totals totals,
                              List<SessionDtos.SessionSummary> sessions, List<DailyAbsence> exceptions) {
    }
}
