package om.mtc.attendance.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.AuthService;
import om.mtc.attendance.web.dto.AuthDtos.ChangePasswordRequest;
import om.mtc.attendance.web.dto.AuthDtos.LoginRequest;
import om.mtc.attendance.web.dto.AuthDtos.TokenResponse;
import om.mtc.attendance.web.dto.UserDtos.UserView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Authentication. The access token is returned in the body (kept in memory by the
 * client); the rotating refresh token travels only in an HttpOnly, SameSite=Strict
 * cookie scoped to /api/auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String REFRESH_COOKIE = "mtc_rt";

    private final AuthService auth;
    private final boolean secureCookie;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.secureCookie = props.security().refreshCookieSecure();
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
        return respond(auth.login(r.username(), r.password(), req.getRemoteAddr()), res);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                 HttpServletRequest req, HttpServletResponse res) {
        return respond(auth.refresh(token, req.getRemoteAddr()), res);
    }

    @PostMapping("/logout")
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token, HttpServletResponse res) {
        auth.logout(token);
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    @GetMapping("/me")
    public UserView me() {
        return auth.me(SecurityUtils.currentUser());
    }

    @PostMapping("/change-password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest r, HttpServletRequest req,
                                        HttpServletResponse res) {
        return respond(auth.changePassword(SecurityUtils.currentUser(), r.currentPassword(), r.newPassword(),
                req.getRemoteAddr()), res);
    }

    private TokenResponse respond(AuthService.Tokens t, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(t.refreshToken(), t.refreshTtl()).toString());
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new TokenResponse(t.accessToken(), t.expiresInSeconds(), t.user());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
