package om.mtc.attendance.web.error;

import java.time.Instant;
import java.util.Map;

/** Uniform JSON error body: {code, message, fieldErrors?, timestamp}. */
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors, Instant timestamp) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors, Instant.now());
    }
}
