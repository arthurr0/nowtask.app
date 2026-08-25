package app.nowtask.integrations;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import app.nowtask.integrations.GitHubRows.Installation;
import app.nowtask.integrations.api.Channels.Delivery;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class GitHubWebhookService {

    record Outcome(boolean accepted, String detail) {
    }

    private final ObjectMapper json;
    private final GitHubStore store;
    private final GitHubActor actor;
    private final GitHubPushEvents pushes;
    private final GitHubPullEvents pulls;
    private final GitHubIssueEvents issues;
    private final GitHubBuildEvents builds;
    private final IntegrationRepository integrations;
    private final DeliveryLog log;

    GitHubWebhookService(
            ObjectMapper json,
            GitHubStore store,
            GitHubActor actor,
            GitHubPushEvents pushes,
            GitHubPullEvents pulls,
            GitHubIssueEvents issues,
            GitHubBuildEvents builds,
            IntegrationRepository integrations,
            DeliveryLog log) {
        this.json = json;
        this.store = store;
        this.actor = actor;
        this.pushes = pushes;
        this.pulls = pulls;
        this.issues = issues;
        this.builds = builds;
        this.integrations = integrations;
        this.log = log;
    }

    Outcome handle(String event, String deliveryId, String body) {
        if ("ping".equals(event)) {
            return new Outcome(true, "pong");
        }

        JsonNode payload = json.readTree(body);
        long installationId = payload.path("installation").path("id").asLong(0);
        if (installationId == 0) {
            return new Outcome(false, "The event carries no installation");
        }

        if ("installation".equals(event) && "deleted".equals(payload.path("action").asString(""))) {
            store.release(installationId, Instant.now());
            return new Outcome(true, "The installation was released");
        }

        Optional<Installation> installation = store.lookup(installationId);
        if (installation.isEmpty()) {
            return new Outcome(false, "Installation " + installationId + " belongs to no organization here");
        }

        boolean ran = actor.runAs(installation.get(),
                () -> inside(installation.get(), event, deliveryId, payload));

        return ran
                ? new Outcome(true, "handled")
                : new Outcome(false, "The organization no longer grants the connecting account access");
    }

    private void inside(Installation installation, String event, String deliveryId, JsonNode payload) {
        if (deliveryId != null && !deliveryId.isBlank() && !store.firstDelivery(deliveryId, event)) {
            return;
        }

        if ("installation".equals(event)) {
            String action = payload.path("action").asString("");
            if ("suspend".equals(action) || "unsuspend".equals(action)) {
                store.suspend(installation.installationId(), "suspend".equals(action));
            }
            return;
        }

        String repo = payload.path("repository").path("full_name").asString("");

        for (Integration integration : integrations.findByEnabledTrue()) {
            if (!GitHubSettings.KIND.equals(integration.getKind())) {
                continue;
            }

            GitHubSettings settings = GitHubSettings.of(integration.getConfig());
            if (!repo.isBlank() && !settings.covers(repo)) {
                continue;
            }

            dispatch(new GitHubEvent(event, integration, settings, repo,
                    installation.organizationId(), installation.installationId(), payload));
        }
    }

    private void dispatch(GitHubEvent event) {
        try {
            switch (event.name()) {
                case "push" -> pushes.onPush(event);
                case "create" -> pushes.onCreate(event);
                case "delete" -> pushes.onDelete(event);
                case "pull_request" -> pulls.onPullRequest(event);
                case "pull_request_review" -> pulls.onReview(event);
                case "pull_request_review_comment" -> pulls.onReviewComment(event);
                case "issues" -> issues.onIssue(event);
                case "issue_comment" -> issues.onIssueComment(event);
                case "workflow_run" -> builds.onWorkflowRun(event);
                case "release" -> builds.onRelease(event);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            log.record(event.integration().getId(), "github." + event.name(), null,
                    new Delivery(false, e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
