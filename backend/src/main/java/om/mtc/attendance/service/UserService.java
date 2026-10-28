package om.mtc.attendance.service;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.repository.AppUserRepository;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.repository.RefreshTokenRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.UserDtos.CreateUserRequest;
import om.mtc.attendance.web.dto.UserDtos.UpdateUserRequest;
import om.mtc.attendance.web.dto.UserDtos.UserView;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** User administration — company agent only (enforced in the controller and re-checked here). */
@Service
public class UserService {
    private final AppUserRepository users;
    private final PlatoonRepository platoons;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PhotoService photos;

    public UserService(AppUserRepository users, PlatoonRepository platoons, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, AuditService audit, PhotoService photos) {
        this.users = users;
        this.platoons = platoons;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> search(CurrentUser cu, Role role, Long platoonId, String q, int page, int size) {
        requireAgent(cu);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        var p = users.search(role, platoonId, like,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("fullName")));
        return PageResponse.of(p.map(UserView::of));
    }

    @Transactional
    public UserView create(CurrentUser cu, CreateUserRequest r) {
        requireAgent(cu);
        String username = r.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "اسم المستخدم مستخدم مسبقاً");
        }
        PasswordPolicy.check(r.temporaryPassword(), username);
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(r.fullName().trim());
        u.setMilitaryRank(blankToNull(r.militaryRank()));
        u.setRole(r.role());
        u.setPlatoon(resolvePlatoon(r.role(), r.platoonId()));
        u.setPasswordHash(encoder.encode(r.temporaryPassword()));
        u.setMustChangePassword(true);
        u.setEnabled(true);
        users.save(u);
        audit.log("USER_CREATED", "USER", u.getId(), "username=" + username + " role=" + r.role()
                + " platoon=" + u.getPlatoonId());
        return UserView.of(u);
    }

    @Transactional
    public UserView update(CurrentUser cu, Long id, UpdateUserRequest r) {
        requireAgent(cu);
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم"));
        boolean self = Objects.equals(u.getId(), cu.id());
        if (self && (!r.enabled() || r.role() != Role.COMPANY_AGENT)) {
            throw ApiException.badRequest("SELF_LOCKOUT", "لا يمكنك تعطيل حسابك أو تغيير دورك");
        }
        boolean wasActiveAgent = u.getRole() == Role.COMPANY_AGENT && u.isEnabled();
        boolean staysActiveAgent = r.role() == Role.COMPANY_AGENT && r.enabled();
        if (wasActiveAgent && !staysActiveAgent && users.countByRoleAndEnabledTrue(Role.COMPANY_AGENT) <= 1) {
            throw ApiException.badRequest("LAST_ADMIN", "يجب أن يبقى وكيل سرية واحد على الأقل مفعّلاً");
        }
        String before = "role=" + u.getRole() + " platoon=" + u.getPlatoonId() + " enabled=" + u.isEnabled();
        u.setFullName(r.fullName().trim());
        u.setMilitaryRank(blankToNull(r.militaryRank()));
        u.setRole(r.role());
        u.setPlatoon(resolvePlatoon(r.role(), r.platoonId()));
        boolean disabling = u.isEnabled() && !r.enabled();
        u.setEnabled(r.enabled());
        users.save(u);
        if (disabling) {
            refreshTokens.revokeAllForUser(u.getId());
        }
        audit.log("USER_UPDATED", "USER", u.getId(), before + " -> role=" + u.getRole() + " platoon="
                + u.getPlatoonId() + " enabled=" + u.isEnabled());
        return UserView.of(u);
    }

    @Transactional
    public UserView resetPassword(CurrentUser cu, Long id, String temporaryPassword) {
        requireAgent(cu);
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم"));
        PasswordPolicy.check(temporaryPassword, u.getUsername());
        u.setPasswordHash(encoder.encode(temporaryPassword));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        refreshTokens.revokeAllForUser(u.getId());
        audit.log("USER_PASSWORD_RESET", "USER", u.getId(), "username=" + u.getUsername());
        return UserView.of(u);
    }

    @Transactional
    public UserView unlock(CurrentUser cu, Long id) {
        requireAgent(cu);
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم"));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.log("USER_UNLOCKED", "USER", u.getId(), "username=" + u.getUsername());
        return UserView.of(u);
    }

    private Platoon resolvePlatoon(Role role, Long platoonId) {
        if (role.isPlatoonBound()) {
            if (platoonId == null) {
                throw ApiException.badRequest("PLATOON_REQUIRED", "يجب تحديد الفصيل لهذا الدور");
            }
            return platoons.findById(platoonId).orElseThrow(() -> ApiException.notFound("الفصيل"));
        }
        return null; // general senior and company agent are unit-wide
    }

    private static void requireAgent(CurrentUser cu) {
        if (!cu.is(Role.COMPANY_AGENT)) {
            throw ApiException.forbidden();
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ profile photos

    /** A user may see/change his own photo; the company agent may manage everybody's. */
    private AppUser requireSelfOrAgent(CurrentUser cu, Long id) {
        if (!Objects.equals(cu.id(), id) && !cu.is(Role.COMPANY_AGENT)) {
            throw ApiException.forbidden();
        }
        return users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم"));
    }

    @Transactional(readOnly = true)
    public byte[] photo(CurrentUser cu, Long id) {
        AppUser u = requireSelfOrAgent(cu, id);
        if (u.getPhotoUpdatedAt() == null) {
            throw ApiException.notFound("الصورة");
        }
        return photos.load(u);
    }

    @Transactional
    public UserView uploadPhoto(CurrentUser cu, Long id, org.springframework.web.multipart.MultipartFile file) {
        AppUser u = requireSelfOrAgent(cu, id);
        byte[] data = ImageGuard.readValidated(file);
        photos.save(u, photos.toAvatar(data, null), cu.id());
        users.save(u);
        audit.log("PHOTO_SET", "USER", id, null);
        return UserView.of(u);
    }

    @Transactional
    public UserView deletePhoto(CurrentUser cu, Long id) {
        AppUser u = requireSelfOrAgent(cu, id);
        photos.delete(u);
        users.save(u);
        audit.log("PHOTO_DELETED", "USER", id, null);
        return UserView.of(u);
    }
}
