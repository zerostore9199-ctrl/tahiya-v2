package om.mtc.attendance.security;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Role;

/** The authenticated principal, rebuilt from the database on every request. */
public record CurrentUser(Long id, String username, String fullName, Role role, Long platoonId,
                          boolean mustChangePassword) {

    public static CurrentUser of(AppUser u) {
        return new CurrentUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getPlatoonId(),
                u.isMustChangePassword());
    }

    public boolean is(Role r) {
        return role == r;
    }
}
