package app.nowtask.identity.api;

import java.time.Instant;

public record EmailChangeView(String newEmail, Instant requestedAt, Instant expiresAt) {
}
