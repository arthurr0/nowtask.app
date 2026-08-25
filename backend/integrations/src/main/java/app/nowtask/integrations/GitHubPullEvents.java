package app.nowtask.integrations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.GitHubRows.Link;
import tools.jackson.databind.JsonNode;

@Component
class GitHubPullEvents {

    private final GitHubTaskOps ops;

    GitHubPullEvents(GitHubTaskOps ops) {
        this.ops = ops;
    }

    void onPullRequest(GitHubEvent event) {
        JsonNode pull = event.payload().path("pull_request");
        String action = event.action();
        int number = pull.path("number").asInt(0);
        String title = pull.path("title").asString("");
        String url = pull.path("html_url").asString("");
        String author = pull.path("user").path("login").asString("");
        boolean merged = pull.path("merged").asBoolean(false);
        String state = merged ? "merged" : pull.path("state").asString("");
        boolean draft = pull.path("draft").asBoolean(false);

        for (String taskKey : keysOf(pull, event)) {
            Optional<Link> existing = ops.linkTo(event.repo(), "pull", number);
            ops.link(Link.pull(existing.map(Link::id).orElseGet(UUID::randomUUID), taskKey, event.repo(),
                    number, pull.path("node_id").asString(""), url, state, title, author,
                    draft ? "draft" : ""));

            switch (action) {
                case "opened", "reopened", "ready_for_review" -> {
                    if (event.settings().commentOnPull()) {
                        ops.comment(event, taskKey, "GitHub, pull request #" + number + " " + action
                                + ": " + title + "\n" + url);
                    }
                    if (!draft) {
                        ops.moveTo(event, taskKey, event.settings().statusOnPullOpen());
                    }
                    if (event.settings().assignFromPull() && "opened".equals(action)) {
                        ops.assignTo(event, taskKey, author, pull.path("user").path("id").asLong(0));
                    }
                }
                case "closed" -> {
                    if (event.settings().commentOnPull()) {
                        ops.comment(event, taskKey, "GitHub, pull request #" + number
                                + (merged ? " merged" : " closed without merging") + ": " + title + "\n" + url);
                    }
                    if (merged) {
                        ops.moveTo(event, taskKey, event.settings().statusOnPullMerged());
                    }
                }
                default -> {
                    continue;
                }
            }

            ops.recordOk(event, taskKey, event.repo() + " #" + number + " " + action);
        }
    }

    void onReview(GitHubEvent event) {
        if (!"submitted".equals(event.action())) {
            return;
        }

        JsonNode review = event.payload().path("review");
        JsonNode pull = event.payload().path("pull_request");
        int number = pull.path("number").asInt(0);
        String verdict = review.path("state").asString("").toLowerCase(java.util.Locale.ROOT);
        String reviewer = review.path("user").path("login").asString("");
        String url = review.path("html_url").asString("");
        String body = review.path("body").asString("");

        for (String taskKey : keysOf(pull, event)) {
            if (event.settings().commentOnReview()) {
                ops.comment(event, taskKey, "GitHub, " + reviewer + " " + wording(verdict)
                        + " pull request #" + number
                        + (body.isBlank() ? "" : ":\n" + body)
                        + "\n" + url);
            }

            switch (verdict) {
                case "approved" -> ops.moveTo(event, taskKey, event.settings().statusOnReviewApproved());
                case "changes_requested" ->
                        ops.moveTo(event, taskKey, event.settings().statusOnReviewChangesRequested());
                default -> {
                }
            }

            ops.linkTo(event.repo(), "pull", number)
                    .ifPresent(link -> ops.link(link.withDetail(verdict)));

            ops.recordOk(event, taskKey, event.repo() + " #" + number + " review " + verdict);
        }
    }

    void onReviewComment(GitHubEvent event) {
        if (!"created".equals(event.action()) || event.fromBot()) {
            return;
        }

        JsonNode comment = event.payload().path("comment");
        JsonNode pull = event.payload().path("pull_request");
        String body = comment.path("body").asString("");

        if (GitHubChannel.fromNowtask(body)) {
            return;
        }

        int number = pull.path("number").asInt(0);
        String author = comment.path("user").path("login").asString("");
        String path = comment.path("path").asString("");

        for (String taskKey : keysOf(pull, event)) {
            if (event.settings().commentOnReview()) {
                ops.comment(event, taskKey, "GitHub, " + author + " on #" + number
                        + (path.isBlank() ? "" : " in " + path) + ":\n" + body
                        + "\n" + comment.path("html_url").asString(""));
            }
            ops.recordOk(event, taskKey, event.repo() + " #" + number + " review comment");
        }
    }

    private List<String> keysOf(JsonNode pull, GitHubEvent event) {
        int number = pull.path("number").asInt(0);
        Optional<Link> existing = ops.linkTo(event.repo(), "pull", number);

        List<String> found = ops.known(event, TaskKeys.find(
                pull.path("head").path("ref").asString(""),
                pull.path("title").asString(""),
                pull.path("body").asString("")));

        if (!found.isEmpty()) {
            return found;
        }

        return existing.map(Link::taskKey).filter(key -> ops.handles(event, key)).map(List::of).orElseGet(List::of);
    }

    private static String wording(String verdict) {
        return switch (verdict) {
            case "approved" -> "approved";
            case "changes_requested" -> "requested changes on";
            case "dismissed" -> "dismissed a review on";
            default -> "reviewed";
        };
    }
}
