package app.nowtask.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiKeyView(
        UUID id,
        String prefix,
        String label,
        Instant lastUsedAt,
        List<ApiKeyScope> scopes,
        UUID ownerId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String state) {
}
