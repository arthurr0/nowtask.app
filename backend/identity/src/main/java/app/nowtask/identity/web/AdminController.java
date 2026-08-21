package app.nowtask.identity.web;

import java.util.List;
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
import app.nowtask.identity.MemberService;
import app.nowtask.identity.RoleService;
import app.nowtask.identity.api.RoleView;
import app.nowtask.identity.api.Audits;
import app.nowtask.identity.api.PermissionView;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final UserDirectory directory;
    private final Audits audits;
    private final MemberService members;
    private final RoleService roles;

    AdminController(UserDirectory directory, Audits audits, MemberService members, RoleService roles) {
        this.directory = directory;
        this.audits = audits;
        this.members = members;
        this.roles = roles;
    }

    @GetMapping("/members")
    List<UserView> members() {
        return directory.findAll();
    }

    record Invite(String name, String email, String role, Integer capacity) {
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    UserView invite(@RequestBody Invite request) {
        return members.invite(request.name(), request.email(), request.role(), request.capacity());
    }

    @PatchMapping("/members/{id}")
    UserView updateMember(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return members.update(id, new PatchBody(body));
    }

    @DeleteMapping("/members/{id}")
    ResponseEntity<Void> removeMember(@PathVariable UUID id) {
        members.remove(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams")
    List<TeamView> teams() {
        return directory.findTeams();
    }

    record TeamBody(String name) {
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    TeamView createTeam(@RequestBody TeamBody request) {
        return members.createTeam(request.name());
    }

    @PatchMapping("/teams/{id}")
    TeamView renameTeam(@PathVariable UUID id, @RequestBody TeamBody request) {
        return members.renameTeam(id, request.name());
    }

    @DeleteMapping("/teams/{id}")
    ResponseEntity<Void> deleteTeam(@PathVariable UUID id) {
        members.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams/{id}/members")
    List<UUID> teamMembers(@PathVariable UUID id) {
        return members.teamMembers(id);
    }

    record TeamMemberBody(UUID userId) {
    }

    @PostMapping("/teams/{id}/members")
    TeamView addTeamMember(@PathVariable UUID id, @RequestBody TeamMemberBody request) {
        return members.addMember(id, request.userId());
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    TeamView removeTeamMember(@PathVariable UUID id, @PathVariable UUID userId) {
        return members.removeMember(id, userId);
    }

    record PermissionCatalog(List<PermissionView> catalog, List<RoleView> roles) {
    }

    @GetMapping("/permissions")
    PermissionCatalog permissions() {
        return new PermissionCatalog(app.nowtask.identity.PermissionsAccess.catalog(), roles.list());
    }

    @GetMapping("/audit")
    Audits.Page audit(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return audits.page(page, size);
    }
}
