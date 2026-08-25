package app.nowtask.integrations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Link;
import app.nowtask.integrations.api.Channels.Delivery;
import app.nowtask.shared.events.TaskCommands;

@Component
class GitHubTaskOps {

    private final TaskCommands tasks;
    private final GitHubStore store;
    private final GitHubAccounts accounts;
    private final GitHubActor actor;
    private final DeliveryLog log;

    GitHubTaskOps(
            TaskCommands tasks,
            GitHubStore store,
            GitHubAccounts accounts,
            GitHubActor actor,
            DeliveryLog log) {
        this.tasks = tasks;
        this.store = store;
        this.accounts = accounts;
        this.actor = actor;
        this.log = log;
    }

    List<String> known(GitHubEvent event, List<String> candidates) {
        return candidates.stream()
                .filter(event.settings()::coversTask)
                .filter(key -> tasks.facts(key).isPresent())
                .toList();
    }

    boolean exists(String taskKey) {
        return taskKey != null && tasks.facts(taskKey).isPresent();
    }

    boolean handles(GitHubEvent event, String taskKey) {
        return taskKey != null && event.settings().coversTask(taskKey) && exists(taskKey);
    }

    void comment(GitHubEvent event, String taskKey, String body) {
        asAuthor(event, () -> tasks.addComment(taskKey, body));
    }

    void moveTo(GitHubEvent event, String taskKey, String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            return;
        }

        try {
            asAuthor(event, () -> tasks.setStatus(taskKey, statusCode));
        } catch (RuntimeException e) {
            record(event, "github.status", taskKey, new Delivery(false,
                    "The task cannot move to " + statusCode + ": " + e.getMessage()));
        }
    }

    void assignTo(GitHubEvent event, String taskKey, String login, Long githubUserId) {
        Optional<UUID> user = accounts.userFor(login, githubUserId);
        if (user.isEmpty()) {
            record(event, "github.assign", taskKey,
                    new Delivery(false, "No nowtask account is linked to " + login));
            return;
        }

        try {
            tasks.assign(taskKey, user.get());
        } catch (RuntimeException e) {
            record(event, "github.assign", taskKey, new Delivery(false, e.getMessage()));
        }
    }

    void applyIssueText(GitHubEvent event, String taskKey, String title, String body) {
        String plain = withoutTaskKey(title, taskKey);

        asAuthor(event, () -> {
            if (!plain.isBlank()) {
                tasks.setTitle(taskKey, plain);
            }
            tasks.setDescription(taskKey, GitHubText.stripMarker(body));
        });
    }

    private static String withoutTaskKey(String title, String taskKey) {
        String trimmed = title == null ? "" : title.trim();
        return trimmed.toUpperCase(java.util.Locale.ROOT).startsWith(taskKey)
                ? trimmed.substring(taskKey.length()).trim()
                : trimmed;
    }

    void link(Link row) {
        store.saveLink(row);
    }

    Optional<Link> linkTo(String repo, String kind, int number) {
        return store.linkByTarget(repo, kind, number);
    }

    void record(GitHubEvent event, String name, String taskKey, Delivery delivery) {
        log.record(event.integration().getId(), name, taskKey, delivery);
    }

    void recordOk(GitHubEvent event, String taskKey, String detail) {
        record(event, "github." + event.name(), taskKey, new Delivery(true, detail));
    }

    private void asAuthor(GitHubEvent event, Runnable work) {
        UUID author = accounts.userFor(event.senderLogin(), event.senderId()).orElse(null);

        if (author == null || !actor.runAsMember(author, event.organizationId(), work)) {
            work.run();
        }
    }
}
