package app.nowtask.identity.api;

import java.time.Instant;
import java.util.UUID;

public record InviteView(
        UUID id,
        String email,
        String roleCode,
        String roleName,
        UUID roleId,
        String state,
        UUID invitedById,
        String invitedByName,
        Instant createdAt,
        Instant expiresAt) {
}
