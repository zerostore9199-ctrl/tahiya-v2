package om.mtc.attendance.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import om.mtc.attendance.domain.AttendanceMethod;
import om.mtc.attendance.domain.AttendanceStatus;
import om.mtc.attendance.domain.SessionStatus;
import om.mtc.attendance.domain.SessionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SessionDtos {
    private SessionDtos() {
    }

    public record OpenSessionRequest(
            @NotNull(message = "الفصيل مطلوب") Long platoonId,
            @NotNull(message = "نوع الجلسة مطلوب") SessionType sessionType,
            @Size(max = 120) String title,
            Instant scheduledAt) {
    }

    /** Attendance counts; attendanceRate = (present + late) / (total - excused), as a percentage. */
    public record Counts(int total, int present, int late, int excused, int absent, int unrecorded,
                         double attendanceRate) {
        public static Counts of(int total, int present, int late, int excused, int absent) {
            int unrecorded = Math.max(0, total - present - late - excused - absent);
            int denom = total - excused;
            double rate = denom <= 0 ? 0 : Math.round(1000.0 * (present + late) / denom) / 10.0;
            return new Counts(total, present, late, excused, absent, unrecorded, rate);
        }
    }

    public record SessionSummary(Long id, Long platoonId, String platoonName, SessionType sessionType,
                                 String title, LocalDate sessionDate, Instant scheduledAt, SessionStatus status,
                                 String openedBy, Instant openedAt, Instant closedAt, String approvedBy,
                                 Instant approvedAt, Counts counts) {
    }

    public record RecordView(Long id, Long soldierId, String militaryNumber, String fullName, String militaryRank,
                             AttendanceStatus status, AttendanceMethod method, Float similarity, String note,
                             Instant recordedAt) {
    }

    public record RosterEntry(Long soldierId, String militaryNumber, String fullName, String militaryRank,
                              boolean hasFaces, Long photoVersion, RecordView record) {
    }

    public record SessionDetail(SessionSummary session, List<RosterEntry> roster) {
    }

    public record ManualMarkRequest(
            @NotNull(message = "الجندي مطلوب") Long soldierId,
            @NotNull(message = "الحالة مطلوبة") AttendanceStatus status,
            @Size(max = 500, message = "الملاحظة طويلة جداً") String note) {
    }

    public record ReviewChange(
            @NotNull Long soldierId,
            @NotNull AttendanceStatus status,
            @Size(max = 500) String note) {
    }

    public record ApproveRequest(@Valid @Size(max = 500) List<ReviewChange> changes) {
    }

    /** One detected face in the uploaded photo and what the system decided about it. */
    public record FaceResult(int[] bbox, float detScore, String outcome, Long soldierId, String fullName,
                             String militaryNumber, Float similarity) {
    }

    public record RecognitionResult(int detected, int ignoredLowQuality, int recognized, int alreadyRecorded,
                                    int unknown, int remaining, int imageWidth, int imageHeight, long elapsedMs,
                                    List<FaceResult> faces, Counts counts) {
    }
}
