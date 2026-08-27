package app.nowtask.shared.events;

import java.time.Instant;
import java.util.UUID;

public final class RealtimeEvents {

    private RealtimeEvents() {
    }

    public record TaskChanged(UUID organizationId, String taskKey, String change, UUID actorId, Instant at) {
    }

    public record WorkspaceChanged(UUID organizationId, String change, UUID actorId, Instant at) {
    }

    public record NotificationPosted(UUID organizationId, UUID userId, String taskKey, Instant at) {
    }
}
