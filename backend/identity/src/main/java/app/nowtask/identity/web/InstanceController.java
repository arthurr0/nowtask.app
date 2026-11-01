package app.nowtask.identity.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.InstanceAuditService;
import app.nowtask.identity.InstanceMailTestService;
import app.nowtask.identity.InstanceOrganizationService;
import app.nowtask.identity.InstanceOverviewService;
import app.nowtask.identity.InstanceSettingsService;
import app.nowtask.identity.InstanceUserService;
import app.nowtask.identity.api.InstanceViews.AuditItem;
import app.nowtask.identity.api.InstanceViews.OrganizationItem;
import app.nowtask.identity.api.InstanceViews.Overview;
import app.nowtask.identity.api.InstanceViews.Page;
import app.nowtask.identity.api.InstanceViews.SettingsView;
import app.nowtask.identity.api.InstanceViews.UserItem;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/instance")
class InstanceController {

    private final UserDirectory directory;
    private final InstanceOverviewService overview;
    private final InstanceOrganizationService organizations;
    private final InstanceUserService users;
    private final InstanceSettingsService settings;
    private final InstanceMailTestService mailTest;
    private final InstanceAuditService audit;

    InstanceController(
            UserDirectory directory,
            InstanceOverviewService overview,
            InstanceOrganizationService organizations,
            InstanceUserService users,
            InstanceSettingsService settings,
            InstanceMailTestService mailTest,
            InstanceAuditService audit) {
        this.directory = directory;
        this.overview = overview;
        this.organizations = organizations;
        this.users = users;
        this.settings = settings;
        this.mailTest = mailTest;
        this.audit = audit;
    }

    @GetMapping("/overview")
    Overview overview() {
        return overview.overview();
    }

    @GetMapping("/organizations")
    Page<OrganizationItem> organizations(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return organizations.list(q, state, page, size);
    }

    @PostMapping("/organizations/{id}/suspend")
    ResponseEntity<Void> suspend(@PathVariable UUID id) {
        organizations.suspend(actor(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/organizations/{id}/restore")
    ResponseEntity<Void> restore(@PathVariable UUID id) {
        organizations.restore(actor(), id);
        return ResponseEntity.noContent().build();
    }

    record DeleteOrganization(String confirmSlug) {
    }

    @DeleteMapping("/organizations/{id}")
    ResponseEntity<Void> deleteOrganization(
            @PathVariable UUID id, @RequestBody(required = false) DeleteOrganization body) {
        organizations.delete(actor(), id, body == null ? null : body.confirmSlug());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    Page<UserItem> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean admin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return users.list(q, state, admin, page, size);
    }

    @PostMapping("/users/{id}/disable")
    ResponseEntity<Void> disable(@PathVariable UUID id) {
        users.disable(actor(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/enable")
    ResponseEntity<Void> enable(@PathVariable UUID id) {
        users.enable(actor(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/sessions/revoke")
    ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {
        users.revokeSessions(actor(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}")
    UserItem updateUser(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        PatchBody patch = new PatchBody(body);
        Boolean instanceAdmin = patch.has("instanceAdmin") ? patch.flag("instanceAdmin") : null;

        if (instanceAdmin == null) {
            throw new IllegalArgumentException("The field instanceAdmin is required");
        }

        return users.setInstanceAdmin(actor(), id, instanceAdmin);
    }

    @GetMapping("/settings")
    SettingsView settings() {
        return settings.settings();
    }

    @PatchMapping("/settings")
    SettingsView updateSettings(@RequestBody Map<String, Object> body) {
        return settings.update(actor(), new PatchBody(body));
    }

    @PostMapping("/mail/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void testMail() {
        mailTest.sendTest(actor());
    }

    @GetMapping("/audit")
    Page<AuditItem> audit(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return audit.page(page, size);
    }

    private UUID actor() {
        return directory.currentUser().id();
    }
}
