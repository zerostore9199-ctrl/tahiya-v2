package om.mtc.attendance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.AppUser;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and verifies short-lived HS256 access tokens (RFC 7519). */
@Service
public class JwtService {
    private static final String ISSUER = "mtc-attendance";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(Math.max(1, props.security().accessTokenMinutes()));
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Parsed, verified token data. */
    public record TokenData(Long userId, Instant issuedAt) {
    }

    public Optional<TokenData> verify(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenData(Long.valueOf(c.getSubject()), c.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
