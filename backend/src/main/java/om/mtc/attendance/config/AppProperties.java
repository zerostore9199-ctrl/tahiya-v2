package om.mtc.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.util.List;

/** Strongly typed application settings (see application.yml, all overridable by env vars). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String zone,
        List<String> corsOrigins,
        Security security,
        Biometric biometric,
        FaceService faceService,
        Reports reports,
        Demo demo) {

    public ZoneId zoneId() {
        return ZoneId.of(zone == null || zone.isBlank() ? "Asia/Muscat" : zone);
    }

    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays,
                           boolean refreshCookieSecure, int maxFailedLogins, int lockoutMinutes,
                           int ipLoginAttemptsPerMinute) {
    }

    public record Biometric(String encryptionKey) {
    }

    public record FaceService(String url, String apiKey, int timeoutSeconds) {
    }

    public record Reports(String pdfFontPath) {
    }

    public record Demo(boolean enabled, String password) {
    }
}
