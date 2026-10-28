package om.mtc.attendance.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Fails fast at startup when a secret is missing or weak, so the system can never
 * run in production with an empty JWT secret or encryption key.
 */
@Component
public class StartupValidator {
    private final AppProperties props;

    public StartupValidator(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validate() {
        String jwt = props.security().jwtSecret();
        if (jwt == null || jwt.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        String key = props.biometric().encryptionKey();
        byte[] decoded;
        try {
            decoded = key == null ? new byte[0] : Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BIOMETRIC_ENC_KEY must be valid Base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("BIOMETRIC_ENC_KEY must decode to exactly 32 bytes (AES-256)");
        }
        String faceKey = props.faceService().apiKey();
        if (faceKey == null || faceKey.length() < 16) {
            throw new IllegalStateException("FACE_API_KEY must be set (at least 16 characters)");
        }
        if (props.demo().enabled() && (props.demo().password() == null || props.demo().password().length() < 10)) {
            throw new IllegalStateException("DEMO_PASSWORD must be at least 10 characters when demo data is enabled");
        }
    }
}
