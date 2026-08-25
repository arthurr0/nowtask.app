package app.nowtask.integrations;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

record GitHubEvent(
        String name,
        Integration integration,
        GitHubSettings settings,
        String repo,
        UUID organizationId,
        long installationId,
        JsonNode payload) {

    String action() {
        return payload.path("action").asString("");
    }

    String senderLogin() {
        return payload.path("sender").path("login").asString("");
    }

    Long senderId() {
        long id = payload.path("sender").path("id").asLong(0);
        return id == 0 ? null : id;
    }

    boolean fromBot() {
        return "Bot".equals(payload.path("sender").path("type").asString(""));
    }
}
