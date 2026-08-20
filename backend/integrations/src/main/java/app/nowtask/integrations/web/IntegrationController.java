package app.nowtask.integrations.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.integrations.IntegrationService;
import app.nowtask.integrations.api.IntegrationViews.IntegrationView;
import app.nowtask.integrations.api.IntegrationViews.TestResult;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/integrations")
class IntegrationController {

    private final IntegrationService integrations;
    private final AuditLog audit;

    IntegrationController(IntegrationService integrations, AuditLog audit) {
        this.integrations = integrations;
        this.audit = audit;
    }

    @GetMapping
    List<IntegrationView> list() {
        return integrations.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IntegrationView create(@RequestBody IntegrationService.NewIntegration request) {
        IntegrationView created = integrations.create(request);
        audit.record("integration.create", created.name(), Map.of("kind", created.kind()));
        return created;
    }

    @PatchMapping("/{id}")
    IntegrationView update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        IntegrationView updated = integrations.update(id, new PatchBody(body));
        audit.record("integration.update", updated.name(), Map.of("fields", List.copyOf(body.keySet())));
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        integrations.delete(id);
        audit.record("integration.delete", id.toString(), Map.of());
    }

    @PostMapping("/{id}/test")
    TestResult test(@PathVariable UUID id) {
        TestResult result = integrations.test(id);
        audit.record("integration.test", id.toString(), Map.of("ok", result.ok(), "detail", result.detail()));
        return result;
    }
}
