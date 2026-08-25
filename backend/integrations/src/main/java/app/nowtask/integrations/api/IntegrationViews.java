package app.nowtask.integrations.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IntegrationViews {

    private IntegrationViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IntegrationView(
            UUID id,
            String kind,
            String name,
            boolean enabled,
            Map<String, Object> config,
            Instant createdAt,
            String lastStatus,
            Instant lastAt,
            String lastDetail,
            List<DeliveryView> deliveries) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryView(UUID id, Instant at, String event, String taskKey, boolean ok, String detail) {
    }

    public record GitHubAccountView(
            boolean available,
            boolean connected,
            String login,
            String avatarUrl) {
    }

    public record GitHubStatusView(
            boolean available,
            String installUrl,
            boolean connected,
            String account,
            boolean suspended,
            List<String> repositories,
            String detail) {
    }

    public record TestResult(boolean ok, String detail) {
    }
}
