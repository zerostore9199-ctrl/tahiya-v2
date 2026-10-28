package om.mtc.attendance.security;

import om.mtc.attendance.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter on login attempts per client IP (complements the
 * per-account lockout stored in the database).
 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MS = 60_000;

    private final int maxPerWindow;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(AppProperties props) {
        this.maxPerWindow = Math.max(1, props.security().ipLoginAttemptsPerMinute());
    }

    /** Records an attempt and returns false when the IP exceeded its budget. */
    public boolean tryAcquire(String ip) {
        String k = ip == null ? "unknown" : ip;
        long now = System.currentTimeMillis();
        Deque<Long> q = attempts.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= maxPerWindow) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > WINDOW_MS;
            }
        });
    }
}
