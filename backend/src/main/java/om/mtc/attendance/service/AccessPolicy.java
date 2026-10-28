package om.mtc.attendance.service;

import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Central, server-side implementation of the permission matrix (جدول 3).
 * Every service call that touches platoon data goes through here, so the scope is
 * enforced in the queries themselves and not only in the user interface.
 */
@Component
public class AccessPolicy {
    private final PlatoonRepository platoons;

    public AccessPolicy(PlatoonRepository platoons) {
        this.platoons = platoons;
    }

    /** Platoon ids whose data the user may read. */
    public List<Long> readablePlatoonIds(CurrentUser u) {
        if (u.role().isPlatoonBound()) {
            return u.platoonId() == null ? List.of() : List.of(u.platoonId());
        }
        return platoons.findAll().stream().map(Platoon::getId).toList();
    }

    public boolean canRead(CurrentUser u, Long platoonId) {
        if (platoonId == null) {
            return false;
        }
        return !u.role().isPlatoonBound() || Objects.equals(u.platoonId(), platoonId);
    }

    public void requireRead(CurrentUser u, Long platoonId) {
        if (!canRead(u, platoonId)) {
            throw ApiException.forbidden();
        }
    }

    /** Camera and manual attendance: senior & platoon leader (own platoon), company agent (any). */
    public void requireTakeAttendance(CurrentUser u, Long platoonId) {
        boolean ok = switch (u.role()) {
            case SENIOR, PLATOON_LEADER -> Objects.equals(u.platoonId(), platoonId);
            case COMPANY_AGENT -> true;
            case GENERAL_SENIOR -> false;
        };
        if (!ok) {
            throw ApiException.forbidden();
        }
    }

    /** Approving absences and lateness: platoon leader (own), company agent. */
    public void requireApprove(CurrentUser u, Long platoonId) {
        boolean ok = (u.is(Role.PLATOON_LEADER) && Objects.equals(u.platoonId(), platoonId))
                || u.is(Role.COMPANY_AGENT);
        if (!ok) {
            throw ApiException.forbidden();
        }
    }

    /** Managing soldiers and enrolling faces: platoon leader (own), company agent. */
    public void requireManageSoldiers(CurrentUser u, Long platoonId) {
        requireApprove(u, platoonId);
    }

    /** Reports: general senior & company agent (all), platoon leader (own). Seniors have no report access. */
    public void requireReports(CurrentUser u, Long platoonIdOrNull) {
        boolean ok = switch (u.role()) {
            case GENERAL_SENIOR, COMPANY_AGENT -> true;
            case PLATOON_LEADER -> platoonIdOrNull == null || Objects.equals(u.platoonId(), platoonIdOrNull);
            case SENIOR -> false;
        };
        if (!ok) {
            throw ApiException.forbidden();
        }
    }
}
