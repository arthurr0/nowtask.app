package app.nowtask.integrations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.integrations.GitHubRows.Installation;
import app.nowtask.integrations.GitHubRows.Link;
import app.nowtask.integrations.api.Channels.Delivery;
import app.nowtask.integrations.api.GitHubTasks;

@Service
public class GitHubTaskService implements GitHubTasks {

    private final GitHubStore store;
    private final GitHubChannel channel;
    private final GitHubClient client;
    private final GitHubApp app;

    GitHubTaskService(GitHubStore store, GitHubChannel channel, GitHubClient client, GitHubApp app) {
        this.store = store;
        this.channel = channel;
        this.client = client;
        this.app = app;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskLink> linksOf(String taskKey) {
        if (!app.configured() || taskKey == null || taskKey.isBlank()) {
            return List.of();
        }

        return store.linksByTask(taskKey).stream()
                .map(link -> new TaskLink(link.kind(), link.repo(), link.number(), link.ref(), link.url(),
                        link.state(), link.title(), link.authorLogin(), link.detail(), link.checkState(),
                        link.updatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenPull(String taskKey) {
        return pulls(taskKey).anyMatch(link -> "open".equalsIgnoreCase(link.state()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasMergedPull(String taskKey) {
        return pulls(taskKey).anyMatch(link -> "merged".equalsIgnoreCase(link.state()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean lastWorkflowFailed(String taskKey) {
        return store.linksByTask(taskKey).stream()
                .filter(link -> "workflow".equals(link.kind()))
                .findFirst()
                .map(link -> "failure".equalsIgnoreCase(link.checkState())
                        || "timed_out".equalsIgnoreCase(link.checkState()))
                .orElse(false);
    }

    @Override
    @Transactional
    public Delivery comment(String taskKey, String body) {
        if (body == null || body.isBlank()) {
            return new Delivery(false, "The comment is empty");
        }

        Optional<Link> target = commentTarget(taskKey);
        if (target.isEmpty()) {
            return new Delivery(false, "The task " + taskKey + " has no issue or pull request on GitHub");
        }

        try {
            channel.comment(target.get(), body);
            return new Delivery(true, target.get().repo() + " #" + target.get().number());
        } catch (GitHubException e) {
            return new Delivery(false, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Delivery closeIssue(String taskKey) {
        Optional<Link> issue = store.issueLink(taskKey);
        Optional<Installation> installation = usable();

        if (issue.isEmpty()) {
            return new Delivery(false, "The task " + taskKey + " has no issue on GitHub");
        }
        if (installation.isEmpty()) {
            return new Delivery(false, "The GitHub App is not usable here");
        }

        try {
            client.updateIssue(installation.get().installationId(), issue.get().repo(), issue.get().number(),
                    Map.of("state", "closed"));
            store.markSynced(issue.get().id(), issue.get().syncedTitle(), issue.get().syncedBody(), "closed");
            return new Delivery(true, issue.get().repo() + " #" + issue.get().number() + " closed");
        } catch (GitHubException e) {
            return new Delivery(false, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Delivery addLabel(String taskKey, String label) {
        Optional<Link> issue = store.issueLink(taskKey);
        Optional<Installation> installation = usable();

        if (label == null || label.isBlank()) {
            return new Delivery(false, "The label is empty");
        }
        if (issue.isEmpty()) {
            return new Delivery(false, "The task " + taskKey + " has no issue on GitHub");
        }
        if (installation.isEmpty()) {
            return new Delivery(false, "The GitHub App is not usable here");
        }

        try {
            client.addLabels(installation.get().installationId(), issue.get().repo(), issue.get().number(),
                    List.of(label.trim()));
            return new Delivery(true, issue.get().repo() + " #" + issue.get().number() + " labelled");
        } catch (GitHubException e) {
            return new Delivery(false, e.getMessage());
        }
    }

    private java.util.stream.Stream<Link> pulls(String taskKey) {
        return store.linksByTask(taskKey).stream().filter(link -> "pull".equals(link.kind()));
    }

    private Optional<Link> commentTarget(String taskKey) {
        return store.linksByTask(taskKey).stream()
                .filter(link -> "issue".equals(link.kind()) || "pull".equals(link.kind()))
                .filter(link -> link.number() != null)
                .findFirst();
    }

    private Optional<Installation> usable() {
        if (!app.configured()) {
            return Optional.empty();
        }
        return store.current().filter(installation -> !installation.suspended());
    }
}
