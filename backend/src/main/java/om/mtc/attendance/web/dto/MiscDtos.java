package om.mtc.attendance.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import om.mtc.attendance.domain.AuditLog;

import java.time.Instant;
import java.util.List;

public final class MiscDtos {
    private MiscDtos() {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }

    public record AuditView(Long id, Instant occurredAt, String username, String action, String entityType,
                            String entityId, String details, String ipAddress, boolean success) {
        public static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getOccurredAt(), a.getUsername(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getIpAddress(), a.isSuccess());
        }
    }

    public record SettingsView(double recognitionThreshold, double recognitionMinDetScore,
                               double enrollmentMinDetScore, int enrollmentMaxFaces,
                               double enrollmentDuplicateThreshold, int lateGraceMinutes, int auditRetentionDays) {
    }

    public record SettingsUpdate(
            @NotNull @DecimalMin("0.20") @DecimalMax("0.90") Double recognitionThreshold,
            @NotNull @DecimalMin("0.30") @DecimalMax("0.99") Double recognitionMinDetScore,
            @NotNull @DecimalMin("0.30") @DecimalMax("0.99") Double enrollmentMinDetScore,
            @NotNull @Min(1) @Max(10) Integer enrollmentMaxFaces,
            @NotNull @DecimalMin("0.30") @DecimalMax("0.95") Double enrollmentDuplicateThreshold,
            @NotNull @Min(0) @Max(120) Integer lateGraceMinutes,
            @NotNull @Min(365) @Max(3650) Integer auditRetentionDays) {
    }
}
