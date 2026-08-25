package app.nowtask.integrations;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Link;
import tools.jackson.databind.JsonNode;

@Component
class GitHubPushEvents {

    private final GitHubTaskOps ops;

    GitHubPushEvents(GitHubTaskOps ops) {
        this.ops = ops;
    }

    void onPush(GitHubEvent event) {
        String branch = branchOf(event.payload().path("ref").asString(""));
        GitHubSettings settings = event.settings();
        Set<String> moved = new HashSet<>();

        for (JsonNode commit : event.payload().path("commits")) {
            String message = commit.path("message").asString("");
            String url = commit.path("url").asString("");
            String author = commit.path("author").path("name").asString("");
            String sha = commit.path("id").asString("");

            for (String taskKey : ops.known(event, TaskKeys.find(message, branch))) {
                if (settings.linkCommits() && !sha.isBlank()) {
                    ops.link(Link.commit(taskKey, event.repo(), sha, url, firstLine(message), author));
                }
                if (settings.commentOnPush()) {
                    ops.comment(event, taskKey, "GitHub, " + event.repo() + "@" + branch + ": "
                            + firstLine(message)
                            + (author.isBlank() ? "" : " (" + author + ")")
                            + (url.isBlank() ? "" : "\n" + url)
                            + (sha.isBlank() ? "" : "\n" + shortSha(sha)));
                }
                if (moved.add(taskKey)) {
                    ops.moveTo(event, taskKey, settings.statusOnBranchPush());
                }
                ops.recordOk(event, taskKey, event.repo() + "@" + branch);
            }
        }
    }

    void onCreate(GitHubEvent event) {
        String type = event.payload().path("ref_type").asString("");
        if (!"branch".equals(type)) {
            return;
        }

        String branch = event.payload().path("ref").asString("");
        for (String taskKey : ops.known(event, TaskKeys.find(branch))) {
            ops.link(Link.named("branch", taskKey, event.repo(), branch,
                    "https://github.com/" + event.repo() + "/tree/" + branch, "open", branch,
                    event.senderLogin(), "", ""));

            if (event.settings().commentOnBranch()) {
                ops.comment(event, taskKey, "GitHub, branch " + branch + " created in " + event.repo());
            }
            ops.moveTo(event, taskKey, event.settings().statusOnBranchCreated());
            ops.recordOk(event, taskKey, event.repo() + " branch " + branch);
        }
    }

    void onDelete(GitHubEvent event) {
        String type = event.payload().path("ref_type").asString("");
        if (!"branch".equals(type)) {
            return;
        }

        String branch = event.payload().path("ref").asString("");
        for (String taskKey : ops.known(event, TaskKeys.find(branch))) {
            ops.link(Link.named("branch", taskKey, event.repo(), branch,
                    "https://github.com/" + event.repo(), "deleted", branch,
                    event.senderLogin(), "", ""));

            if (event.settings().commentOnBranch()) {
                ops.comment(event, taskKey, "GitHub, branch " + branch + " deleted in " + event.repo());
            }
            ops.recordOk(event, taskKey, event.repo() + " branch " + branch + " deleted");
        }
    }

    private static String firstLine(String message) {
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }

    private static String shortSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private static String branchOf(String ref) {
        return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
    }
}
