package app.nowtask.integrations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Installation;
import app.nowtask.integrations.GitHubRows.Link;
import app.nowtask.integrations.api.Channels.Delivery;

@Component
class GitHubChannel {

    private static final String MARKER = "<!-- nowtask -->";

    private final GitHubApp app;
    private final GitHubClient client;
    private final GitHubStore store;
    private final String appUrl;

    GitHubChannel(
            GitHubApp app,
            GitHubClient client,
            GitHubStore store,
            @Value("${nowtask.app.url:http://localhost:8080}") String appUrl) {
        this.app = app;
        this.client = client;
        this.store = store;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    Delivery check(Integration integration) {
        if (!app.configured()) {
            return new Delivery(false, "The GitHub App is not configured on this instance");
        }

        Optional<Installation> installation = store.current();
        if (installation.isEmpty()) {
            return new Delivery(false, "The organization has not installed the GitHub App yet");
        }
        if (installation.get().suspended()) {
            return new Delivery(false, "The GitHub App installation is suspended");
        }

        try {
            List<String> repositories = client.repositories(installation.get().installationId());
            GitHubSettings settings = GitHubSettings.of(integration.getConfig());

            for (String repo : settings.repos()) {
                if (repositories.stream().noneMatch(name -> name.equalsIgnoreCase(repo))) {
                    return new Delivery(false, "The installation has no access to " + repo);
                }
            }

            return new Delivery(true, "Connected as " + installation.get().accountLogin()
                    + ", " + repositories.size() + " repositories available");
        } catch (GitHubException e) {
            return new Delivery(false, e.getMessage());
        }
    }

    Optional<Link> openIssue(GitHubSettings settings, String taskKey, String title, String description) {
        String repo = settings.issueRepo();
        if (repo.isBlank()) {
            return Optional.empty();
        }

        Optional<Installation> installation = usable();
        if (installation.isEmpty()) {
            return Optional.empty();
        }

        String body = issueBody(taskKey, description);
        GitHubIssue issue = client.createIssue(
                installation.get().installationId(), repo, issueTitle(taskKey, title), body, List.of());

        Link link = Link.issue(UUID.randomUUID(), taskKey, repo, issue.number(), issue.nodeId(),
                issue.url(), issue.state(), issue.title(), "nowtask", "", issue.title(), body);
        store.saveLink(link);
        return Optional.of(link);
    }

    void pushIssue(Link link, String taskKey, String title, String description, boolean closed) {
        Optional<Installation> installation = usable();
        if (installation.isEmpty()) {
            return;
        }

        String body = issueBody(taskKey, description);
        String subject = issueTitle(taskKey, title);
        String state = closed ? "closed" : "open";

        if (subject.equals(link.syncedTitle()) && body.equals(link.syncedBody())
                && state.equals(link.state())) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", subject);
        fields.put("body", body);
        fields.put("state", state);

        GitHubIssue issue =
                client.updateIssue(installation.get().installationId(), link.repo(), link.number(), fields);
        store.markSynced(link.id(), issue.title(), body, issue.state());
    }

    void comment(Link link, String body) {
        usable().ifPresent(installation ->
                client.comment(installation.installationId(), link.repo(), link.number(), MARKER + "\n" + body));
    }

    private Optional<Installation> usable() {
        if (!app.configured()) {
            return Optional.empty();
        }
        return store.current().filter(installation -> !installation.suspended());
    }

    private String issueTitle(String taskKey, String title) {
        return taskKey + " " + (title == null ? "" : title.trim());
    }

    private String issueBody(String taskKey, String description) {
        String text = description == null ? "" : description.trim();
        return MARKER + "\n" + (text.isBlank() ? "" : text + "\n\n")
                + "---\n[" + taskKey + "](" + appUrl + "/app/tasks/" + taskKey + ") in nowtask";
    }

    static boolean fromNowtask(String body) {
        return body != null && body.contains(MARKER);
    }
}
