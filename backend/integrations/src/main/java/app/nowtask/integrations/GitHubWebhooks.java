package app.nowtask.integrations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhooks {

    public record Reply(boolean rejected, String detail) {
    }

    private final GitHubApp app;
    private final GitHubWebhookService webhooks;
    private final AsyncTaskExecutor executor;

    GitHubWebhooks(
            GitHubApp app,
            GitHubWebhookService webhooks,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.app = app;
        this.webhooks = webhooks;
        this.executor = executor;
    }

    public Reply receive(String event, String delivery, String signature, byte[] raw, String body) {
        if (!app.configured()) {
            return new Reply(true, "The GitHub App is not configured on this instance");
        }
        if (event == null || event.isBlank()) {
            return new Reply(true, "The event header is missing");
        }
        if (!GitHubSignature.matches(app.webhookSecret(), raw, signature)) {
            return new Reply(true, "The signature does not match");
        }
        if ("ping".equals(event)) {
            return new Reply(false, "pong");
        }

        executor.execute(() -> webhooks.handle(event, delivery, body));
        return new Reply(false, "accepted");
    }
}
