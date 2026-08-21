package app.nowtask.identity.web;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.InviteService;
import app.nowtask.identity.api.BulkInviteResultView;
import app.nowtask.identity.api.InviteView;

@RestController
@RequestMapping("/api/organization/invites")
class AdminInviteController {

    private final InviteService invites;

    AdminInviteController(InviteService invites) {
        this.invites = invites;
    }

    @GetMapping
    List<InviteView> list(@RequestParam(required = false) String state) {
        return invites.list(state);
    }

    record NewInvite(@NotBlank String email, String role) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InviteView create(@RequestBody NewInvite request) {
        return invites.invite(request.email(), request.role());
    }

    record BulkInvite(List<String> emails, String role) {
    }

    @PostMapping("/bulk")
    BulkInviteResultView createBulk(@RequestBody BulkInvite request) {
        return invites.inviteBulk(request.emails(), request.role());
    }

    @PostMapping("/{id}/resend")
    InviteView resend(@PathVariable UUID id) {
        return invites.resend(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@PathVariable UUID id) {
        invites.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
