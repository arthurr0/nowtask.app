package app.nowtask.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class GitHubOAuth {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";

    private final GitHubApp app;
    private final ObjectMapper json;
    private final HttpClient http;

    GitHubOAuth(GitHubApp app, ObjectMapper json) {
        this.app = app;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    boolean available() {
        return !app.clientId().isBlank() && !app.clientSecret().isBlank();
    }

    String authorizeUrl(String state, String redirectUri) {
        return AUTHORIZE_URL
                + "?client_id=" + URLEncoder.encode(app.clientId(), StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    GitHubUser identity(String userToken) {
        JsonNode body = get("/user", userToken);
        long id = body.path("id").asLong(0);
        String login = body.path("login").asString("");

        if (id == 0 || login.isBlank()) {
            throw new GitHubException("GitHub returned no account for this authorization");
        }

        return new GitHubUser(id, login, body.path("avatar_url").asString(""));
    }

    String exchange(String code) {
        String body = json.writeValueAsString(Map.of(
                "client_id", app.clientId(),
                "client_secret", app.clientSecret(),
                "code", code));

        JsonNode response = post(TOKEN_URL, body, null);
        String token = response.path("access_token").asString("");
        if (token.isBlank()) {
            throw new GitHubException("GitHub did not return a user token: "
                    + response.path("error_description").asString("unknown reason"));
        }
        return token;
    }

    Set<Long> installationsOf(String userToken) {
        JsonNode response = get("/user/installations?per_page=100", userToken);
        Set<Long> ids = new LinkedHashSet<>();
        response.path("installations").valueStream()
                .forEach(entry -> ids.add(entry.path("id").asLong(0)));
        return ids;
    }

    private JsonNode post(String url, String body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", GitHubApi.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }

        return send(request.build());
    }

    private JsonNode get(String path, String token) {
        return send(HttpRequest.newBuilder(URI.create(GitHubApi.BASE + path))
                .timeout(TIMEOUT)
                .header("Accept", GitHubApi.ACCEPT)
                .header("X-GitHub-Api-Version", GitHubApi.VERSION)
                .header("User-Agent", GitHubApi.USER_AGENT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build());
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("The GitHub authorization was interrupted");
        } catch (Exception e) {
            throw new GitHubException("GitHub is unreachable: " + e.getMessage());
        }

        if (response.statusCode() / 100 != 2) {
            throw new GitHubException("GitHub refused the authorization: HTTP " + response.statusCode());
        }

        return json.readTree(response.body());
    }
}
