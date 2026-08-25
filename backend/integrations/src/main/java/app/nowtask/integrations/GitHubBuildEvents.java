package app.nowtask.integrations;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Link;
import tools.jackson.databind.JsonNode;

@Component
class GitHubBuildEvents {

    private final GitHubTaskOps ops;

    GitHubBuildEvents(GitHubTaskOps ops) {
        this.ops = ops;
    }

    void onWorkflowRun(GitHubEvent event) {
        if (!"completed".equals(event.action())) {
            return;
        }

        JsonNode run = event.payload().path("workflow_run");
        String conclusion = run.path("conclusion").asString("").toLowerCase(Locale.ROOT);
        String name = run.path("name").asString("");
        String branch = run.path("head_branch").asString("");
        String url = run.path("html_url").asString("");
        String runId = String.valueOf(run.path("id").asLong(0));
        boolean failed = "failure".equals(conclusion) || "timed_out".equals(conclusion);

        List<String> keys = ops.known(event, TaskKeys.find(branch, run.path("head_commit").path("message")
                .asString(""), run.path("display_title").asString("")));

        for (String taskKey : keys) {
            ops.link(Link.named("workflow", taskKey, event.repo(), runId, url, conclusion, name,
                    run.path("actor").path("login").asString(""), branch, conclusion));

            if (event.settings().commentOnWorkflow()) {
                ops.comment(event, taskKey, "GitHub, workflow " + name + " on " + branch + " ended as "
                        + conclusion + "\n" + url);
            }
            if (failed) {
                ops.moveTo(event, taskKey, event.settings().statusOnWorkflowFailure());
            }

            ops.recordOk(event, taskKey, event.repo() + " " + name + " " + conclusion);
        }
    }

    void onRelease(GitHubEvent event) {
        if (!"published".equals(event.action())) {
            return;
        }

        JsonNode release = event.payload().path("release");
        String tag = release.path("tag_name").asString("");
        String name = release.path("name").asString("");
        String url = release.path("html_url").asString("");
        String body = release.path("body").asString("");

        for (String taskKey : ops.known(event, TaskKeys.find(body, name, tag))) {
            ops.link(Link.named("release", taskKey, event.repo(), tag, url, "published",
                    name.isBlank() ? tag : name, release.path("author").path("login").asString(""), tag, ""));

            if (event.settings().commentOnRelease()) {
                ops.comment(event, taskKey, "GitHub, released " + (name.isBlank() ? tag : name)
                        + " in " + event.repo() + "\n" + url);
            }

            ops.recordOk(event, taskKey, event.repo() + " release " + tag);
        }
    }
}
