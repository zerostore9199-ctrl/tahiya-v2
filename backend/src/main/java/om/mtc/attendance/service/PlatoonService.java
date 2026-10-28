package om.mtc.attendance.service;

import om.mtc.attendance.domain.Platoon;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.repository.PlatoonRepository;
import om.mtc.attendance.repository.SoldierRepository;
import om.mtc.attendance.security.CurrentUser;
import om.mtc.attendance.web.dto.PlatoonDtos.PlatoonRequest;
import om.mtc.attendance.web.dto.PlatoonDtos.PlatoonView;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatoonService {
    private final PlatoonRepository platoons;
    private final SoldierRepository soldiers;
    private final AccessPolicy access;
    private final AuditService audit;

    public PlatoonService(PlatoonRepository platoons, SoldierRepository soldiers, AccessPolicy access,
                          AuditService audit) {
        this.platoons = platoons;
        this.soldiers = soldiers;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlatoonView> list(CurrentUser cu) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : soldiers.countActiveByPlatoon()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return platoons.findAllByOrderByNameAsc().stream()
                .filter(p -> access.canRead(cu, p.getId()))
                .map(p -> PlatoonView.of(p, counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    public PlatoonView create(CurrentUser cu, PlatoonRequest r) {
        requireAgent(cu);
        String code = r.code().trim().toUpperCase();
        if (platoons.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("PLATOON_CODE_TAKEN", "رمز الفصيل مستخدم مسبقاً");
        }
        Platoon p = new Platoon();
        p.setCode(code);
        p.setName(r.name().trim());
        if (r.companyName() != null && !r.companyName().isBlank()) {
            p.setCompanyName(r.companyName().trim());
        }
        p.setActive(r.active() == null || r.active());
        platoons.save(p);
        audit.log("PLATOON_CREATED", "PLATOON", p.getId(), "code=" + code);
        return PlatoonView.of(p, 0);
    }

    @Transactional
    public PlatoonView update(CurrentUser cu, Long id, PlatoonRequest r) {
        requireAgent(cu);
        Platoon p = platoons.findById(id).orElseThrow(() -> ApiException.notFound("الفصيل"));
        String code = r.code().trim().toUpperCase();
        if (!code.equalsIgnoreCase(p.getCode()) && platoons.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("PLATOON_CODE_TAKEN", "رمز الفصيل مستخدم مسبقاً");
        }
        p.setCode(code);
        p.setName(r.name().trim());
        if (r.companyName() != null && !r.companyName().isBlank()) {
            p.setCompanyName(r.companyName().trim());
        }
        if (r.active() != null) {
            p.setActive(r.active());
        }
        platoons.save(p);
        audit.log("PLATOON_UPDATED", "PLATOON", p.getId(), "code=" + code + " active=" + p.isActive());
        return PlatoonView.of(p, soldiers.countByPlatoon_IdAndActiveTrue(p.getId()));
    }

    private static void requireAgent(CurrentUser cu) {
        if (!cu.is(Role.COMPANY_AGENT)) {
            throw ApiException.forbidden();
        }
    }
}
