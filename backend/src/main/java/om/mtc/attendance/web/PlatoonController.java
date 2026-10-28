package om.mtc.attendance.web;

import jakarta.validation.Valid;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.PlatoonService;
import om.mtc.attendance.web.dto.PlatoonDtos.PlatoonRequest;
import om.mtc.attendance.web.dto.PlatoonDtos.PlatoonView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platoons")
public class PlatoonController {
    private final PlatoonService platoons;

    public PlatoonController(PlatoonService platoons) {
        this.platoons = platoons;
    }

    /** Lists the platoons visible to the caller (own platoon for seniors and leaders). */
    @GetMapping
    public List<PlatoonView> list() {
        return platoons.list(SecurityUtils.currentUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY_AGENT')")
    public PlatoonView create(@Valid @RequestBody PlatoonRequest r) {
        return platoons.create(SecurityUtils.currentUser(), r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_AGENT')")
    public PlatoonView update(@PathVariable Long id, @Valid @RequestBody PlatoonRequest r) {
        return platoons.update(SecurityUtils.currentUser(), id, r);
    }
}
