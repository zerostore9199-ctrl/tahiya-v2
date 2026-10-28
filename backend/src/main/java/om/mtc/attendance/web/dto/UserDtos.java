package om.mtc.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Role;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserView(Long id, String username, String fullName, String militaryRank, Role role,
                           Long platoonId, String platoonName, boolean enabled, boolean mustChangePassword,
                           boolean locked, Long photoVersion, Instant lastLoginAt, Instant createdAt) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getMilitaryRank(), u.getRole(),
                    u.getPlatoonId(), u.getPlatoon() == null ? null : u.getPlatoon().getName(),
                    u.isEnabled(), u.isMustChangePassword(), u.isLocked(Instant.now()),
                    u.getPhotoUpdatedAt() == null ? null : u.getPhotoUpdatedAt().toEpochMilli(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank(message = "اسم المستخدم مطلوب")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "اسم المستخدم: 3-50 حرفاً لاتينياً أو أرقاماً")
            String username,
            @NotBlank(message = "الاسم الكامل مطلوب") @Size(max = 120) String fullName,
            @Size(max = 50) String militaryRank,
            @NotNull(message = "الدور مطلوب") Role role,
            Long platoonId,
            @NotBlank(message = "كلمة المرور المؤقتة مطلوبة")
            @Size(min = 10, max = 128, message = "كلمة المرور يجب أن تكون بين 10 و128 حرفاً") String temporaryPassword) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "الاسم الكامل مطلوب") @Size(max = 120) String fullName,
            @Size(max = 50) String militaryRank,
            @NotNull(message = "الدور مطلوب") Role role,
            Long platoonId,
            boolean enabled) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 10, max = 128, message = "كلمة المرور يجب أن تكون بين 10 و128 حرفاً")
            String temporaryPassword) {
    }
}
