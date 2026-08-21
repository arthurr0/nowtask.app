package app.nowtask.identity.api;

import java.time.Instant;

public record InvitePreviewView(
        String organizationName,
        String organizationSlug,
        String invitedByName,
        String role,
        String maskedEmail,
        Instant expiresAt,
        String state,
        boolean accountExists,
        boolean ssoAvailable) {

    public static InvitePreviewView unknown() {
        return new InvitePreviewView("", "", "", "", "", null, "unknown", false, false);
    }
}
