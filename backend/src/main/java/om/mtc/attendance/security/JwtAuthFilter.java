package om.mtc.attendance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.web.error.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates "Authorization: Bearer" requests. The user is re-loaded from the
 * database on every request, so disabling an account, changing a role or changing
 * a password takes effect immediately rather than when the token expires.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, AppUserRepository users, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            Optional<JwtService.TokenData> data = jwtService.verify(header.substring(7).trim());
            if (data.isPresent()) {
                Optional<AppUser> user = users.findWithPlatoon(data.get().userId());
                if (user.isPresent() && isTokenStillValid(user.get(), data.get().issuedAt())) {
                    AppUser u = user.get();
                    CurrentUser cu = CurrentUser.of(u);
                    if (cu.mustChangePassword() && !isAllowedDuringPasswordChange(request)) {
                        writeError(response, 403, "PASSWORD_CHANGE_REQUIRED",
                                "يجب تغيير كلمة المرور قبل متابعة استخدام النظام");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(cu, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isTokenStillValid(AppUser u, Instant issuedAt) {
        if (!u.isEnabled() || u.isLocked(Instant.now())) {
            return false;
        }
        // Tokens issued before the last password change are rejected.
        Instant changed = u.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS);
        return !issuedAt.isBefore(changed);
    }

    private boolean isAllowedDuringPasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
    }
}
