package om.mtc.attendance.web;

import jakarta.validation.Valid;
import om.mtc.attendance.domain.Role;
import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.UserService;
import om.mtc.attendance.web.dto.MiscDtos.PageResponse;
import om.mtc.attendance.web.dto.UserDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('COMPANY_AGENT')")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserView> search(@RequestParam(required = false) Role role,
                                         @RequestParam(required = false) Long platoonId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return users.search(SecurityUtils.currentUser(), role, platoonId, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest r) {
        return users.create(SecurityUtils.currentUser(), r);
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        return users.update(SecurityUtils.currentUser(), id, r);
    }

    @PostMapping("/{id}/reset-password")
    public UserView resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest r) {
        return users.resetPassword(SecurityUtils.currentUser(), id, r.temporaryPassword());
    }

    @PostMapping("/{id}/unlock")
    public UserView unlock(@PathVariable Long id) {
        return users.unlock(SecurityUtils.currentUser(), id);
    }
}
