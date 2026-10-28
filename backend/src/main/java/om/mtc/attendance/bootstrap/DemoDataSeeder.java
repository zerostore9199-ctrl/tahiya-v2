package om.mtc.attendance.bootstrap;

import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.domain.Soldier;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.repository.SoldierRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds fictional demo data when DEMO_DATA_ENABLED=true and the database is empty.
 * All demo accounts share DEMO_PASSWORD (from the environment — never hard-coded)
 * and are NOT forced to change it, so the demo can be explored immediately.
 * Never enable this in production.
 */
@Component
@org.springframework.core.annotation.Order(1)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] FIRST = {"سالم", "خالد", "محمد", "سعيد", "علي", "حمد", "ناصر", "يوسف", "أحمد",
            "عبدالله", "سيف", "مازن", "هلال", "راشد", "فهد", "طلال", "بدر", "حمود", "مالك", "زاهر"};
    private static final String[] LAST = {"البلوشي", "الحارثي", "الكندي", "الهنائي", "المعمري", "الشكيلي",
            "العبري", "الرواحي", "السعدي", "الغافري", "البوسعيدي", "الناعبي"};
    private static final String[] RANKS = {"جندي", "جندي أول", "عريف", "وكيل عريف"};

    private final AppProperties props;
    private final PlatoonRepository platoons;
    private final AppUserRepository users;
    private final SoldierRepository soldiers;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(AppProperties props, PlatoonRepository platoons, AppUserRepository users,
                          SoldierRepository soldiers, PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.props = props;
        this.platoons = platoons;
        this.users = users;
        this.soldiers = soldiers;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo().enabled()) {
            return;
        }
        // Several backend replicas may start together: only one seeds, the others wait and then see the data.
        jdbc.execute("select pg_advisory_xact_lock(727301)");
        if (users.count() > 0) {
            return;
        }
        String hash = encoder.encode(props.demo().password());
        Platoon p1 = platoon("P1", "الفصيل الأول");
        Platoon p2 = platoon("P2", "الفصيل الثاني");
        Platoon p3 = platoon("P3", "الفصيل الثالث");

        user("agent", "وكيل السرية", "رائد", Role.COMPANY_AGENT, null, hash);
        user("general", "السنير العام", "رقيب أول", Role.GENERAL_SENIOR, null, hash);
        user("leader1", "قائد الفصيل الأول", "ملازم أول", Role.PLATOON_LEADER, p1, hash);
        user("leader2", "قائد الفصيل الثاني", "ملازم", Role.PLATOON_LEADER, p2, hash);
        user("senior1", "سنير الفصيل الأول", "رقيب", Role.SENIOR, p1, hash);
        user("senior2", "سنير الفصيل الثاني", "رقيب", Role.SENIOR, p2, hash);
        user("senior3", "سنير الفصيل الثالث", "رقيب", Role.SENIOR, p3, hash);

        int n = 0;
        for (Platoon p : new Platoon[]{p1, p2, p3}) {
            for (int i = 0; i < 12; i++, n++) {
                Soldier s = new Soldier();
                s.setMilitaryNumber(String.format("9%05d", n + 1));
                s.setFullName(FIRST[(n * 7) % FIRST.length] + " " + FIRST[(n * 3 + 5) % FIRST.length] + " "
                        + LAST[n % LAST.length]);
                s.setMilitaryRank(RANKS[n % RANKS.length]);
                s.setPlatoon(p);
                soldiers.save(s);
            }
        }
        log.warn("Demo data created: 3 platoons, 7 users (agent, general, leader1, leader2, senior1-3), {} soldiers. "
                + "Disable DEMO_DATA_ENABLED in production.", n);
    }

    private Platoon platoon(String code, String name) {
        Platoon p = new Platoon();
        p.setCode(code);
        p.setName(name);
        p.setCompanyName("السرية الأولى");
        return platoons.save(p);
    }

    private void user(String username, String name, String rank, Role role, Platoon p, String hash) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(name);
        u.setMilitaryRank(rank);
        u.setRole(role);
        u.setPlatoon(p);
        u.setPasswordHash(hash);
        u.setMustChangePassword(false);
        users.save(u);
    }
}
