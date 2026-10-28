package om.mtc.attendance.bootstrap;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.repository.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production first-run: when the database has no users and BOOTSTRAP_ADMIN_PASSWORD
 * is provided, creates a single company-agent account that must change its password
 * on first login. Runs after the demo seeder, which wins if demo data is enabled.
 */
@Component
@Order(10)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository users, JdbcTemplate jdbc, PasswordEncoder encoder,
                          @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String username,
                          @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Several backend replicas may start together: only one seeds, the others wait and then see the data.
        jdbc.execute("select pg_advisory_xact_lock(727301)");
        if (users.count() > 0) {
            return;
        }
        if (password == null || password.length() < 12) {
            log.warn("No users exist. Set BOOTSTRAP_ADMIN_PASSWORD (12+ chars) to create the first administrator.");
            return;
        }
        AppUser u = new AppUser();
        u.setUsername(username.toLowerCase());
        u.setFullName("مدير النظام");
        u.setRole(Role.COMPANY_AGENT);
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(true);
        users.save(u);
        log.warn("Bootstrap administrator '{}' created; password change is required at first login.", username);
    }
}
