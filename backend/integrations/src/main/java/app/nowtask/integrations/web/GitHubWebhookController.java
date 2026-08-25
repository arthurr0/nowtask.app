package app.nowtask.integrations.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.integrations.GitHubWebhooks;

@RestController
@RequestMapping("/api/integrations/github/webhook")
class GitHubWebhookController {

    private final GitHubWebhooks webhooks;

    GitHubWebhookController(GitHubWebhooks webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping
    ResponseEntity<Map<String, String>> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        GitHubWebhooks.Reply reply = webhooks.receive(
                event, delivery, signature, body, new String(body, StandardCharsets.UTF_8));

        return ResponseEntity.status(reply.rejected() ? HttpStatus.UNAUTHORIZED : HttpStatus.OK)
                .body(Map.of("detail", reply.detail()));
    }
}
