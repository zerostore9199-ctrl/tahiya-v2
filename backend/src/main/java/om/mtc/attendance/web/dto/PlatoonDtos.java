package om.mtc.attendance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import om.mtc.attendance.domain.Platoon;

public final class PlatoonDtos {
    private PlatoonDtos() {
    }

    public record PlatoonView(Long id, String code, String name, String companyName, boolean active,
                              long activeSoldiers) {
        public static PlatoonView of(Platoon p, long activeSoldiers) {
            return new PlatoonView(p.getId(), p.getCode(), p.getName(), p.getCompanyName(), p.isActive(),
                    activeSoldiers);
        }
    }

    public record PlatoonRequest(
            @NotBlank(message = "رمز الفصيل مطلوب")
            @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$", message = "الرمز: حتى 20 حرفاً لاتينياً أو رقماً") String code,
            @NotBlank(message = "اسم الفصيل مطلوب") @Size(max = 100) String name,
            @Size(max = 100) String companyName,
            Boolean active) {
    }
}
