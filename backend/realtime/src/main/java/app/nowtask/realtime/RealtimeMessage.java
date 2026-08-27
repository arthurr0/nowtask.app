package app.nowtask.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeMessage(String type, String taskKey, String change, UUID actorId, Instant at) {

    public static RealtimeMessage task(String taskKey, String change, UUID actorId, Instant at) {
        return new RealtimeMessage("task", taskKey, change, actorId, at);
    }

    public static RealtimeMessage workspace(String change, UUID actorId, Instant at) {
        return new RealtimeMessage("workspace", null, change, actorId, at);
    }

    public static RealtimeMessage notification(String taskKey, Instant at) {
        return new RealtimeMessage("notification", taskKey, "posted", null, at);
    }
}
