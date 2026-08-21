package app.nowtask.identity.api;

import java.time.Instant;
import java.util.UUID;

public record OrganizationView(
        UUID id,
        String name,
        String slug,
        String ssoDomain,
        String defaultPresetCode,
        String state,
        Instant createdAt) {
}
