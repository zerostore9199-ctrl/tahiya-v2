package om.mtc.attendance.security;

import jakarta.servlet.http.HttpServletRequest;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static Optional<CurrentUser> currentUserOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return Optional.of(cu);
        }
        return Optional.empty();
    }

    public static CurrentUser currentUser() {
        return currentUserOptional().orElseThrow(ApiException::unauthorized);
    }

    public static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}
