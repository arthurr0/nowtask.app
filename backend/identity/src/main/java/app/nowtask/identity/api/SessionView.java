package app.nowtask.identity.api;

import java.time.Instant;
import java.util.UUID;

public record SessionView(
        UUID id,
        Instant createdAt,
        Instant lastSeenAt,
        String ip,
        String userAgent,
        boolean current) {
}
