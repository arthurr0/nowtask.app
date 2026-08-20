package app.nowtask.identity.web;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.ApiKeyScope;
import app.nowtask.identity.api.ApiKeyView;
import app.nowtask.identity.api.ApiKeys;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.RoleId;

@RestController
@RequestMapping("/api/admin/api-keys")
class ApiKeyController {
    private final ApiKeys keys;
    private final UserDirectory directory;
    private final AuditLog audit;

    ApiKeyController(ApiKeys keys, UserDirectory directory, AuditLog audit) {
        this.keys = keys;
        this.directory = directory;
        this.audit = audit;
    }

    @GetMapping
    List<ApiKeyView> list() {
        requireAdministrator();
        return keys.list();
    }

    record NewApiKey(String label, List<String> scopes, Integer expiresInDays) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiKeys.Issued create(@RequestBody NewApiKey request) {
        requireAdministrator();

        Set<ApiKeyScope> scopes = new LinkedHashSet<>();
        if (request.scopes() != null) {
            request.scopes().forEach(code -> scopes.add(ApiKeyScope.of(code)));
        }

        ApiKeys.Issued issued = keys.issue(request.label(), scopes, request.expiresInDays());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("label", issued.view().label());
        detail.put("scopes", issued.view().scopes().stream().map(ApiKeyScope::code).toList());
        detail.put("expiresAt", issued.view().expiresAt());
        audit.record("api-key.created", issued.view().prefix(), detail);
        return issued;
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> revoke(@PathVariable UUID id) {
        requireAdministrator();
        keys.revoke(id);
        audit.record("api-key.revoked", id.toString(), Map.of());
        return ResponseEntity.noContent().build();
    }

    private void requireAdministrator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ApiKeyIdentity) {
            throw new AccessDeniedException("An API key cannot manage API keys");
        }
        if (directory.currentUser().role() != RoleId.ADMIN) {
            throw new AccessDeniedException("Managing API keys requires the administrator role");
        }
    }
}
