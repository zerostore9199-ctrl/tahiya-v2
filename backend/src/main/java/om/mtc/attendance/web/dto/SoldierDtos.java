package om.mtc.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import om.mtc.attendance.domain.Soldier;

import java.time.Instant;

public final class SoldierDtos {
    private SoldierDtos() {
    }

    public record SoldierView(Long id, String militaryNumber, String fullName, String militaryRank,
                              Long platoonId, String platoonName, boolean active, boolean biometricConsent,
                              long enrolledFaces, Long photoVersion, Instant createdAt) {
        public static SoldierView of(Soldier s, long enrolledFaces) {
            return new SoldierView(s.getId(), s.getMilitaryNumber(), s.getFullName(), s.getMilitaryRank(),
                    s.getPlatoonId(), s.getPlatoon().getName(), s.isActive(), s.isBiometricConsent(),
                    enrolledFaces, s.getPhotoUpdatedAt() == null ? null : s.getPhotoUpdatedAt().toEpochMilli(),
                    s.getCreatedAt());
        }
    }

    public record SoldierRequest(
            @NotBlank(message = "الرقم العسكري مطلوب")
            @Pattern(regexp = "^[0-9A-Za-z-]{2,30}$", message = "الرقم العسكري غير صالح") String militaryNumber,
            @NotBlank(message = "الاسم الكامل مطلوب") @Size(max = 120) String fullName,
            @NotBlank(message = "الرتبة مطلوبة") @Size(max = 50) String militaryRank,
            @NotNull(message = "الفصيل مطلوب") Long platoonId,
            boolean biometricConsent) {
    }

    /** Result of saving a soldier's photo; {@code enrollment} is null when the photo was stored without enrolling the face. */
    public record PhotoResult(SoldierView soldier, EnrollResult enrollment) {
    }

    public record FaceTemplateView(Long id, String modelName, float detScore, Instant createdAt) {
    }

    public record EnrollResult(int enrolledFaces, int maxFaces, float detScore, FaceTemplateView template) {
    }
}
