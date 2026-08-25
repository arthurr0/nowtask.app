package app.nowtask.integrations;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

record GitHubSettings(
        List<String> repos,
        List<String> projects,
        String issueRepo,
        String statusOnPullOpen,
        String statusOnPullMerged,
        String statusOnIssueClosed,
        String statusOnIssueReopened,
        String statusOnBranchPush,
        String statusOnBranchCreated,
        String statusOnReviewApproved,
        String statusOnReviewChangesRequested,
        String statusOnWorkflowFailure,
        List<String> issueClosingStatuses,
        boolean syncIssues,
        boolean commentOnPull,
        boolean commentOnPush,
        boolean commentOnIssueComment,
        boolean commentOnReview,
        boolean commentOnWorkflow,
        boolean commentOnRelease,
        boolean commentOnBranch,
        boolean assignFromPull,
        boolean linkCommits) {

    static final String KIND = "github";

    static GitHubSettings of(Map<String, Object> config) {
        Map<String, Object> source = config == null ? Map.of() : config;

        return new GitHubSettings(
                list(source.get("repos")),
                list(source.get("projects")),
                text(source, "issueRepo"),
                text(source, "statusOnPullOpen"),
                text(source, "statusOnPullMerged"),
                text(source, "statusOnIssueClosed"),
                text(source, "statusOnIssueReopened"),
                text(source, "statusOnBranchPush"),
                text(source, "statusOnBranchCreated"),
                text(source, "statusOnReviewApproved"),
                text(source, "statusOnReviewChangesRequested"),
                text(source, "statusOnWorkflowFailure"),
                list(source.get("issueClosingStatuses")),
                flag(source, "syncIssues", true),
                flag(source, "commentOnPull", true),
                flag(source, "commentOnPush", true),
                flag(source, "commentOnIssueComment", true),
                flag(source, "commentOnReview", true),
                flag(source, "commentOnWorkflow", false),
                flag(source, "commentOnRelease", true),
                flag(source, "commentOnBranch", false),
                flag(source, "assignFromPull", false),
                flag(source, "linkCommits", true));
    }

    boolean closesIssue(String statusCode) {
        return statusCode != null
                && issueClosingStatuses.stream().anyMatch(code -> code.equalsIgnoreCase(statusCode));
    }

    boolean covers(String repo) {
        return repos.isEmpty() || repos.stream().anyMatch(entry -> entry.equalsIgnoreCase(repo));
    }

    boolean coversTask(String taskKey) {
        if (projects.isEmpty()) {
            return true;
        }

        String project = TaskKeys.projectOf(taskKey);
        return !project.isBlank() && projects.stream().anyMatch(entry -> entry.equalsIgnoreCase(project));
    }

    private static List<String> list(Object value) {
        if (value instanceof List<?> entries) {
            return entries.stream()
                    .filter(Objects::nonNull)
                    .map(entry -> entry.toString().trim())
                    .filter(entry -> !entry.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static boolean flag(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equals(value.toString().trim().toLowerCase(Locale.ROOT));
    }
}
