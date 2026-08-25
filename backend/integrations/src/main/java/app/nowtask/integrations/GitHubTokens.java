package app.nowtask.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class GitHubTokens {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EARLY_REFRESH = Duration.ofMinutes(5);

    private record Cached(String token, Instant expiresAt) {

        boolean usable() {
            return expiresAt.minus(EARLY_REFRESH).isAfter(Instant.now());
        }
    }

    private final GitHubApp app;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    GitHubTokens(GitHubApp app, ObjectMapper json) {
        this.app = app;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    String forInstallation(long installationId) {
        Cached cached = cache.get(installationId);
        if (cached != null && cached.usable()) {
            return cached.token();
        }

        Cached fresh = request(installationId);
        cache.put(installationId, fresh);
        return fresh.token();
    }

    void forget(long installationId) {
        cache.remove(installationId);
    }

    private Cached request(long installationId) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(GitHubApi.BASE + "/app/installations/" + installationId + "/access_tokens"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + app.jwt())
                .header("Accept", GitHubApi.ACCEPT)
                .header("X-GitHub-Api-Version", GitHubApi.VERSION)
                .header("User-Agent", GitHubApi.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("The GitHub token request was interrupted");
        } catch (Exception e) {
            throw new GitHubException("GitHub is unreachable: " + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            throw new GitHubException("GitHub refused a token for installation " + installationId
                    + ": HTTP " + response.statusCode());
        }

        JsonNode body = json.readTree(response.body());
        String token = body.path("token").asString("");
        if (token.isBlank()) {
            throw new GitHubException("GitHub returned a token response without a token");
        }

        String expiry = body.path("expires_at").asString("");
        Instant expiresAt = expiry.isBlank() ? Instant.now().plus(Duration.ofHours(1)) : Instant.parse(expiry);
        return new Cached(token, expiresAt);
    }
}
