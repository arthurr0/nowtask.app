package app.nowtask.integrations;

import java.util.UUID;

final class GitHubRows {

    record Installation(
            UUID id,
            UUID organizationId,
            long installationId,
            String accountLogin,
            String accountType,
            boolean suspended,
            UUID connectedBy) {
    }

    record Link(
            UUID id,
            String taskKey,
            String repo,
            String kind,
            Integer number,
            String ref,
            String nodeId,
            String url,
            String state,
            String title,
            String origin,
            String authorLogin,
            String detail,
            String checkState,
            String syncedTitle,
            String syncedBody,
            java.time.Instant updatedAt) {

        static Link issue(
                UUID id,
                String taskKey,
                String repo,
                int number,
                String nodeId,
                String url,
                String state,
                String title,
                String origin,
                String authorLogin,
                String syncedTitle,
                String syncedBody) {
            return new Link(id, taskKey, repo, "issue", number, String.valueOf(number), nodeId, url, state,
                    title, origin, authorLogin, "", "", syncedTitle, syncedBody, null);
        }

        static Link pull(
                UUID id,
                String taskKey,
                String repo,
                int number,
                String nodeId,
                String url,
                String state,
                String title,
                String authorLogin,
                String detail) {
            return new Link(id, taskKey, repo, "pull", number, String.valueOf(number), nodeId, url, state,
                    title, "github", authorLogin, detail, "", "", "", null);
        }

        static Link commit(
                String taskKey, String repo, String sha, String url, String title, String authorLogin) {
            return new Link(UUID.randomUUID(), taskKey, repo, "commit", null, sha, "", url, "",
                    title, "github", authorLogin, "", "", "", "", null);
        }

        static Link named(
                String kind,
                String taskKey,
                String repo,
                String ref,
                String url,
                String state,
                String title,
                String authorLogin,
                String detail,
                String checkState) {
            return new Link(UUID.randomUUID(), taskKey, repo, kind, null, ref, "", url, state, title,
                    "github", authorLogin, detail, checkState, "", "", null);
        }

        Link withDetail(String value) {
            return new Link(id, taskKey, repo, kind, number, ref, nodeId, url, state, title, origin,
                    authorLogin, value, checkState, syncedTitle, syncedBody, updatedAt);
        }

        String shortRef() {
            return "commit".equals(kind) && ref != null && ref.length() > 7 ? ref.substring(0, 7) : ref;
        }
    }

    private GitHubRows() {
    }
}
