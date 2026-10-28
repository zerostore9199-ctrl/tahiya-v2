package om.mtc.attendance.web;

import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.UserService;
import om.mtc.attendance.web.dto.UserDtos.UserView;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** Profile photo of a user: the user himself or the company agent (checked in {@link UserService}). */
@RestController
@RequestMapping("/api/users/{id}/photo")
public class UserPhotoController {
    private final UserService users;

    public UserPhotoController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(users.photo(SecurityUtils.currentUser(), id));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView put(@PathVariable Long id, @RequestPart("image") MultipartFile image) {
        return users.uploadPhoto(SecurityUtils.currentUser(), id, image);
    }

    @DeleteMapping
    public UserView delete(@PathVariable Long id) {
        return users.deletePhoto(SecurityUtils.currentUser(), id);
    }
}
