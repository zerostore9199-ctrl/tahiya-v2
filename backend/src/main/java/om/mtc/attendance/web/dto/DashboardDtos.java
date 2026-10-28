package om.mtc.attendance.web.dto;

import om.mtc.attendance.domain.SessionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record PlatoonLive(Long platoonId, String platoonName, String seniorName, long activeSoldiers,
                              Long sessionId, String sessionType, SessionStatus sessionStatus, Instant sessionOpenedAt,
                              SessionDtos.Counts counts) {
    }

    public record Kpis(long totalSoldiers, int present, int late, int absent, int excused, double attendanceRate) {
    }

    public record TrendPoint(LocalDate date, double attendanceRate, int records) {
    }

    public record PendingApproval(Long sessionId, String platoonName, String sessionType, LocalDate sessionDate,
                                  Instant closedAt, int absent, int late) {
    }

    public record AuditEntry(Instant occurredAt, String username, String action, String details) {
    }

    public record LiveDashboard(LocalDate date, Kpis kpis, List<PlatoonLive> platoons,
                                List<PendingApproval> pendingApprovals, List<AuditEntry> recentActivity) {
    }
}
