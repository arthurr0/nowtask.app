package app.nowtask.shared.events;

import java.time.Instant;
import java.util.UUID;

public final class TaskEvents {

    private TaskEvents() {
    }

    public interface TaskEvent {
        String ruleName();
    }

    public record TaskStatusChanged(
            String taskKey,
            String fromStatusCode,
            String toStatusCode,
            UUID actorId,
            Instant at,
            String ruleName) implements TaskEvent {
    }

    public record TaskAssigned(String taskKey, UUID assigneeId, UUID actorId, Instant at, String ruleName)
            implements TaskEvent {
    }

    public record TaskLabelAdded(String taskKey, String label, UUID actorId, Instant at, String ruleName)
            implements TaskEvent {
    }

    public record TaskCreated(String taskKey, String title, UUID actorId, Instant at, String ruleName)
            implements TaskEvent {
    }

    public record TaskDeleted(String taskKey, UUID actorId, Instant at, String ruleName) implements TaskEvent {
    }
}
