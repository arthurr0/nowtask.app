package app.nowtask.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class GitHubClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final GitHubApp app;
    private final GitHubTokens tokens;
    private final ObjectMapper json;
    private final HttpClient http;

    GitHubClient(GitHubApp app, GitHubTokens tokens, ObjectMapper json) {
        this.app = app;
        this.tokens = tokens;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    GitHubAccount account(long installationId) {
        JsonNode body = call("Bearer " + app.jwt(), "GET",
                "/app/installations/" + installationId, null, installationId);

        return new GitHubAccount(
                body.path("account").path("login").asString(""),
                body.path("account").path("type").asString(""),
                body.path("suspended_at").isNull() || body.path("suspended_at").isMissingNode());
    }

    GitHubIssue createIssue(long installationId, String repo, String title, String body, List<String> labels) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body == null ? "" : body);
        if (labels != null && !labels.isEmpty()) {
            payload.put("labels", labels);
        }

        return issue(send(installationId, "POST", "/repos/" + repo + "/issues", payload));
    }

    GitHubIssue updateIssue(long installationId, String repo, int number, Map<String, Object> fields) {
        return issue(send(installationId, "PATCH", "/repos/" + repo + "/issues/" + number, fields));
    }

    void addLabels(long installationId, String repo, int number, List<String> labels) {
        send(installationId, "POST", "/repos/" + repo + "/issues/" + number + "/labels",
                Map.of("labels", labels));
    }

    void comment(long installationId, String repo, int number, String body) {
        send(installationId, "POST", "/repos/" + repo + "/issues/" + number + "/comments",
                Map.of("body", body));
    }

    GitHubIssue readIssue(long installationId, String repo, int number) {
        return issue(send(installationId, "GET", "/repos/" + repo + "/issues/" + number, null));
    }

    List<String> repositories(long installationId) {
        JsonNode body = send(installationId, "GET", "/installation/repositories?per_page=100", null);
        return body.path("repositories").valueStream()
                .map(entry -> entry.path("full_name").asString(""))
                .filter(name -> !name.isBlank())
                .toList();
    }

    private JsonNode send(long installationId, String method, String path, Map<String, Object> payload) {
        return call("Bearer " + tokens.forInstallation(installationId), method, path, payload, installationId);
    }

    private JsonNode call(
            String authorization, String method, String path, Map<String, Object> payload, long installationId) {
        HttpRequest.BodyPublisher publisher = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(GitHubApi.BASE + path))
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .header("Accept", GitHubApi.ACCEPT)
                .header("X-GitHub-Api-Version", GitHubApi.VERSION)
                .header("User-Agent", GitHubApi.USER_AGENT)
                .method(method, publisher);

        if (payload != null) {
            request.header("Content-Type", "application/json");
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("The GitHub call was interrupted");
        } catch (Exception e) {
            throw new GitHubException("GitHub is unreachable: " + e.getMessage());
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            tokens.forget(installationId);
        }
        if (response.statusCode() / 100 != 2) {
            throw new GitHubException("GitHub refused " + method + " " + path
                    + ": HTTP " + response.statusCode() + " " + detail(response.body()));
        }

        return json.readTree(response.body());
    }

    private String detail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        try {
            return json.readTree(body).path("message").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static GitHubIssue issue(JsonNode node) {
        return new GitHubIssue(
                node.path("number").asInt(0),
                node.path("node_id").asString(""),
                node.path("html_url").asString(""),
                node.path("state").asString(""),
                node.path("title").asString(""),
                node.path("body").asString(""));
    }
}
