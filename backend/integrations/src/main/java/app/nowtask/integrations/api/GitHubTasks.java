package app.nowtask.integrations.api;

import java.time.Instant;
import java.util.List;
import app.nowtask.integrations.api.Channels.Delivery;

public interface GitHubTasks {

    record TaskLink(
            String kind,
            String repo,
            Integer number,
            String ref,
            String url,
            String state,
            String title,
            String authorLogin,
            String detail,
            String checkState,
            Instant updatedAt) {
    }

    List<TaskLink> linksOf(String taskKey);

    boolean hasOpenPull(String taskKey);

    boolean hasMergedPull(String taskKey);

    boolean lastWorkflowFailed(String taskKey);

    Delivery comment(String taskKey, String body);

    Delivery closeIssue(String taskKey);

    Delivery addLabel(String taskKey, String label);
}
