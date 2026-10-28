package om.mtc.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "اسم المستخدم مطلوب") @Size(max = 50) String username,
            @NotBlank(message = "كلمة المرور مطلوبة") @Size(max = 128) String password) {
    }

    public record TokenResponse(String accessToken, long expiresInSeconds, UserDtos.UserView user) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "كلمة المرور الحالية مطلوبة") @Size(max = 128) String currentPassword,
            @NotBlank(message = "كلمة المرور الجديدة مطلوبة") @Size(min = 10, max = 128,
                    message = "كلمة المرور يجب أن تكون بين 10 و128 حرفاً") String newPassword) {
    }
}
