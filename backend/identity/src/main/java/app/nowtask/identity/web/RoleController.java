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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.RoleService;
import app.nowtask.identity.api.RoleView;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/admin/roles")
class RoleController {

    private final RoleService roles;

    RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    List<RoleView> list() {
        return roles.list();
    }

    record NewRole(String code, String name, List<String> permissions) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoleView create(@RequestBody NewRole request) {
        return roles.create(request.code(), request.name(), request.permissions());
    }

    @PatchMapping("/{id}")
    RoleView update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return roles.update(id, new PatchBody(body));
    }

    record DeleteRole(UUID reassignTo) {
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @RequestBody(required = false) DeleteRole request) {
        roles.delete(id, request == null ? null : request.reassignTo());
        return ResponseEntity.noContent().build();
    }
}
