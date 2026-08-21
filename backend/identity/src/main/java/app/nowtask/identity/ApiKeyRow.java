package app.nowtask.identity;

import java.time.Instant;
import java.util.UUID;

record ApiKeyRow(
        UUID id,
        UUID organizationId,
        UUID roleId,
        String prefix,
        String label,
        String tokenHash,
        String scopes,
        UUID ownerId,
        Instant lastUsedAt,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {

    boolean revoked() {
        return revokedAt != null;
    }

    boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    String state(Instant now) {
        if (revoked()) {
            return "revoked";
        }
        if (expired(now)) {
            return "expired";
        }
        return "active";
    }
}
