package app.nowtask.integrations.web;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.integrations.GitHubSetupService;
import app.nowtask.integrations.api.IntegrationViews.GitHubStatusView;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/integrations/github")
class GitHubController {

    private final GitHubSetupService setup;
    private final AuditLog audit;

    GitHubController(GitHubSetupService setup, AuditLog audit) {
        this.setup = setup;
        this.audit = audit;
    }

    @GetMapping
    GitHubStatusView status() {
        return setup.status();
    }

    @PostMapping("/connect")
    GitHubStatusView connect(@RequestBody Map<String, Object> body) {
        PatchBody patch = new PatchBody(body);
        long installationId = installation(patch);

        GitHubStatusView connected = setup.connect(installationId, patch.text("code"));
        audit.record("integration.github.connect", connected.account(),
                Map.of("installationId", installationId));
        return connected;
    }

    @DeleteMapping
    GitHubStatusView disconnect() {
        GitHubStatusView status = setup.disconnect();
        audit.record("integration.github.disconnect", "", Map.of());
        return status;
    }

    private static long installation(PatchBody patch) {
        Object raw = patch.raw("installationId");
        if (raw == null) {
            return 0;
        }

        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
