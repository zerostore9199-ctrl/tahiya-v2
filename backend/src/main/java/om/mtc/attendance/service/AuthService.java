package om.mtc.attendance.service;

import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.RefreshToken;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.repository.RefreshTokenRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.security.JwtService;
import om.mtc.attendance.security.LoginRateLimiter;
import om.mtc.attendance.web.dto.UserDtos.UserView;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {
    /** BCrypt hash of a random string, compared against when the username does not exist (constant-time login). */
    private final String dummyHash;

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final AppProperties.Security cfg;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, LoginRateLimiter rateLimiter, AuditService audit, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.cfg = props.security();
        this.dummyHash = encoder.encode("dummy-" + System.nanoTime());
    }

    /** Result of a successful login/refresh: access token for the body, raw refresh token for the cookie. */
    public record Tokens(String accessToken, long expiresInSeconds, String refreshToken, Duration refreshTtl,
                         UserView user) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Tokens login(String username, String password, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            audit.logIndependent(null, username, "LOGIN_RATE_LIMITED", "USER", null, "ip=" + ip, false);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "محاولات كثيرة، انتظر دقيقة ثم حاول مرة أخرى");
        }
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username.trim());
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            audit.logIndependent(null, username, "LOGIN_FAILED", "USER", null, "unknown user", false);
            throw invalidCredentials();
        }
        AppUser u = found.get();
        Instant now = Instant.now();
        if (u.isLocked(now)) {
            audit.logIndependent(u.getId(), u.getUsername(), "LOGIN_BLOCKED", "USER", u.getId(), "account locked",
                    false);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "تم قفل الحساب مؤقتاً بسبب محاولات دخول خاطئة، حاول لاحقاً");
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            String detail = "attempt " + u.getFailedAttempts();
            if (u.getFailedAttempts() >= cfg.maxFailedLogins()) {
                u.setLockedUntil(now.plus(Duration.ofMinutes(cfg.lockoutMinutes())));
                u.setFailedAttempts(0);
                detail = "account locked for " + cfg.lockoutMinutes() + " min";
            }
            users.save(u);
            audit.logIndependent(u.getId(), u.getUsername(), "LOGIN_FAILED", "USER", u.getId(), detail, false);
            throw invalidCredentials();
        }
        if (!u.isEnabled()) {
            audit.logIndependent(u.getId(), u.getUsername(), "LOGIN_FAILED", "USER", u.getId(), "disabled", false);
            throw invalidCredentials();
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        users.save(u);
        audit.logIndependent(u.getId(), u.getUsername(), "LOGIN_SUCCESS", "USER", u.getId(), null, true);
        return issue(u, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Tokens refresh(String rawToken, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized();
        }
        RefreshToken t = refreshTokens.findByTokenHash(sha256(rawToken)).orElseThrow(ApiException::unauthorized);
        AppUser u = t.getUser();
        if (t.isRevoked()) {
            // A revoked token was presented again: assume theft and revoke the whole family.
            refreshTokens.revokeAllForUser(u.getId());
            audit.logIndependent(u.getId(), u.getUsername(), "REFRESH_TOKEN_REUSE", "USER", u.getId(),
                    "all sessions revoked", false);
            throw ApiException.unauthorized();
        }
        if (t.getExpiresAt().isBefore(Instant.now()) || !u.isEnabled() || u.isLocked(Instant.now())) {
            t.setRevoked(true);
            throw ApiException.unauthorized();
        }
        t.setRevoked(true); // rotation: every refresh token is single-use
        return issue(u, ip);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> {
            t.setRevoked(true);
            audit.logIndependent(t.getUser().getId(), t.getUser().getUsername(), "LOGOUT", "USER",
                    t.getUser().getId(), null, true);
        });
    }

    @Transactional
    public Tokens changePassword(CurrentUser cu, String current, String next, String ip) {
        AppUser u = users.findById(cu.id()).orElseThrow(ApiException::unauthorized);
        if (!encoder.matches(current, u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "كلمة المرور الحالية غير صحيحة");
        }
        if (encoder.matches(next, u.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "كلمة المرور الجديدة يجب أن تختلف عن الحالية");
        }
        PasswordPolicy.check(next, u.getUsername());
        u.setPasswordHash(encoder.encode(next));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(false);
        users.save(u);
        refreshTokens.revokeAllForUser(u.getId());
        audit.log("PASSWORD_CHANGED", "USER", u.getId(), null);
        return issue(u, ip);
    }

    @Transactional(readOnly = true)
    public UserView me(CurrentUser cu) {
        return users.findById(cu.id()).map(UserView::of).orElseThrow(ApiException::unauthorized);
    }

    private Tokens issue(AppUser u, String ip) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Duration ttl = Duration.ofDays(Math.max(1, cfg.refreshTokenDays()));
        RefreshToken t = new RefreshToken();
        t.setUser(u);
        t.setTokenHash(sha256(refresh));
        t.setExpiresAt(Instant.now().plus(ttl));
        t.setCreatedIp(ip);
        refreshTokens.save(t);
        return new Tokens(jwt.issueAccessToken(u), jwt.accessTtl().toSeconds(), refresh, ttl, UserView.of(u));
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "اسم المستخدم أو كلمة المرور غير صحيحة");
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
