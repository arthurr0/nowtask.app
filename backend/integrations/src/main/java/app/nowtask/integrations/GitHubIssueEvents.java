package app.nowtask.integrations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Link;
import tools.jackson.databind.JsonNode;

@Component
class GitHubIssueEvents {

    private final GitHubTaskOps ops;

    GitHubIssueEvents(GitHubTaskOps ops) {
        this.ops = ops;
    }

    void onIssue(GitHubEvent event) {
        if (!event.settings().syncIssues()) {
            return;
        }

        JsonNode issue = event.payload().path("issue");
        String action = event.action();
        int number = issue.path("number").asInt(0);
        String title = issue.path("title").asString("");
        String body = issue.path("body").asString("");
        String state = issue.path("state").asString("");

        Optional<Link> existing = ops.linkTo(event.repo(), "issue", number);
        String taskKey = existing.map(Link::taskKey)
                .filter(key -> ops.handles(event, key))
                .orElseGet(() -> ops.known(event, TaskKeys.find(title, body)).stream().findFirst().orElse(null));

        if (taskKey == null) {
            return;
        }

        if (existing.isPresent() && title.equals(existing.get().syncedTitle())
                && body.equals(existing.get().syncedBody()) && state.equals(existing.get().state())) {
            return;
        }

        switch (action) {
            case "opened" -> {
            }
            case "closed" -> ops.moveTo(event, taskKey, event.settings().statusOnIssueClosed());
            case "reopened" -> ops.moveTo(event, taskKey, event.settings().statusOnIssueReopened());
            case "edited" -> ops.applyIssueText(event, taskKey, title, body);
            default -> {
                return;
            }
        }

        ops.link(Link.issue(
                existing.map(Link::id).orElseGet(UUID::randomUUID),
                taskKey,
                event.repo(),
                number,
                issue.path("node_id").asString(""),
                issue.path("html_url").asString(""),
                state,
                title,
                existing.map(Link::origin).orElse("github"),
                issue.path("user").path("login").asString(""),
                title,
                body));

        ops.recordOk(event, taskKey, event.repo() + " #" + number + " " + action);
    }

    void onIssueComment(GitHubEvent event) {
        if (!event.settings().commentOnIssueComment() || !"created".equals(event.action())) {
            return;
        }

        JsonNode comment = event.payload().path("comment");
        String body = comment.path("body").asString("");

        if (event.fromBot() || "Bot".equals(comment.path("user").path("type").asString(""))
                || GitHubChannel.fromNowtask(body)) {
            return;
        }

        JsonNode issue = event.payload().path("issue");
        int number = issue.path("number").asInt(0);
        boolean pull = !issue.path("pull_request").isMissingNode();
        String author = comment.path("user").path("login").asString("");

        String taskKey = ops.linkTo(event.repo(), pull ? "pull" : "issue", number)
                .map(Link::taskKey)
                .filter(key -> ops.handles(event, key))
                .orElseGet(() -> ops.known(event, TaskKeys.find(issue.path("title").asString(""), body)).stream()
                        .findFirst()
                        .orElse(null));

        if (taskKey == null) {
            return;
        }

        ops.comment(event, taskKey, "GitHub, " + author + " on #" + number + ":\n" + body);
        ops.recordOk(event, taskKey, event.repo() + " #" + number);
    }
}
