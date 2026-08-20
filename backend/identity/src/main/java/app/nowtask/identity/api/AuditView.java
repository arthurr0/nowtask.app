package app.nowtask.identity.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditView(
        UUID id,
        Instant at,
        UUID actorId,
        UUID apiKeyId,
        String actorLabel,
        String action,
        String subject,
        Map<String, Object> detail) {
}
