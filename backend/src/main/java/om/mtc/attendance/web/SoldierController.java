package om.mtc.attendance.web;

import jakarta.validation.Valid;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.SoldierService;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.SoldierDtos.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/soldiers")
public class SoldierController {
    private final SoldierService soldiers;

    public SoldierController(SoldierService soldiers) {
        this.soldiers = soldiers;
    }

    @GetMapping
    public PageResponse<SoldierView> search(@RequestParam(required = false) Long platoonId,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size,
                                            @RequestParam(defaultValue = "fullName") String sort,
                                            @RequestParam(defaultValue = "asc") String dir) {
        return soldiers.search(SecurityUtils.currentUser(), platoonId, active, q, page, size, sort, dir);
    }

    @GetMapping("/{id}")
    public SoldierView get(@PathVariable Long id) {
        return soldiers.get(SecurityUtils.currentUser(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SoldierView create(@Valid @RequestBody SoldierRequest r) {
        return soldiers.create(SecurityUtils.currentUser(), r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SoldierView update(@PathVariable Long id, @Valid @RequestBody SoldierRequest r) {
        return soldiers.update(SecurityUtils.currentUser(), id, r);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SoldierView deactivate(@PathVariable Long id) {
        return soldiers.deactivate(SecurityUtils.currentUser(), id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SoldierView reactivate(@PathVariable Long id) {
        return soldiers.reactivate(SecurityUtils.currentUser(), id);
    }

    @GetMapping("/{id}/faces")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public List<FaceTemplateView> faces(@PathVariable Long id) {
        return soldiers.listFaces(SecurityUtils.currentUser(), id);
    }

    @PostMapping(value = "/{id}/faces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public EnrollResult enroll(@PathVariable Long id, @RequestPart("image") MultipartFile image) {
        return soldiers.enrollFace(SecurityUtils.currentUser(), id, image);
    }

    /** Save the profile photo; {@code enroll=true} also enrolls the face from the same image. */
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public PhotoResult uploadPhoto(@PathVariable Long id, @RequestPart("image") MultipartFile image,
                                   @RequestParam(defaultValue = "false") boolean enroll) {
        return soldiers.uploadPhoto(SecurityUtils.currentUser(), id, image, enroll);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        byte[] jpeg = soldiers.photo(SecurityUtils.currentUser(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(jpeg);
    }

    @DeleteMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public SoldierView deletePhoto(@PathVariable Long id) {
        return soldiers.deletePhoto(SecurityUtils.currentUser(), id);
    }

    @DeleteMapping("/{id}/faces")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATOON_LEADER','COMPANY_AGENT')")
    public void eraseFaces(@PathVariable Long id) {
        soldiers.eraseFaces(SecurityUtils.currentUser(), id);
    }
}
