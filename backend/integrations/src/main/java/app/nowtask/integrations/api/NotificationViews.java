package app.nowtask.integrations.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationViews {

    private NotificationViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationView(
            UUID id,
            Instant at,
            String kind,
            String titleKey,
            Map<String, Object> params,
            String taskKey,
            boolean read) {
    }

    public record NotificationPage(List<NotificationView> items, int unread) {
    }
}
