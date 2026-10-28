package om.mtc.attendance.web;

import jakarta.validation.Valid;
import om.mtc.attendance.domain.SessionStatus;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.AttendanceService;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.SessionDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private static final String TAKERS = "hasAnyRole('SENIOR','PLATOON_LEADER','COMPANY_AGENT')";

    private final AttendanceService attendance;

    public SessionController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping
    public PageResponse<SessionSummary> list(@RequestParam(required = false) Long platoonId,
                                             @RequestParam(required = false) SessionStatus status,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return attendance.list(SecurityUtils.currentUser(), platoonId, status, from, to, page, size);
    }

    @GetMapping("/open")
    public ResponseEntity<SessionSummary> open(@RequestParam Long platoonId) {
        return attendance.openSession(SecurityUtils.currentUser(), platoonId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(TAKERS)
    public SessionSummary openSession(@Valid @RequestBody OpenSessionRequest r) {
        return attendance.open(SecurityUtils.currentUser(), r);
    }

    @GetMapping("/{id}")
    public SessionDetail detail(@PathVariable Long id) {
        return attendance.detail(SecurityUtils.currentUser(), id);
    }

    @PostMapping(value = "/{id}/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(TAKERS)
    public RecognitionResult recognize(@PathVariable Long id, @RequestPart("image") MultipartFile image) {
        return attendance.recognize(SecurityUtils.currentUser(), id, image);
    }

    @PostMapping("/{id}/manual")
    @PreAuthorize(TAKERS)
    public RecordView manual(@PathVariable Long id, @Valid @RequestBody ManualMarkRequest r) {
        return attendance.manual(SecurityUtils.currentUser(), id, r);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(TAKERS)
    public SessionSummary close(@PathVariable Long id) {
        return attendance.close(SecurityUtils.currentUser(), id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SessionSummary approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest r) {
        return attendance.approve(SecurityUtils.currentUser(), id, r);
    }
}
