package app.nowtask.shared.events;

import java.time.Instant;
import java.util.UUID;

public final class IdentityEvents {

    private IdentityEvents() {
    }

    public record InviteIssued(
            UUID organizationId,
            UUID inviteId,
            String organizationName,
            String email,
            String inviterName,
            String roleName,
            String token,
            Instant expiresAt,
            String locale,
            boolean reminder) {
    }

    public record InviteAccepted(
            UUID organizationId,
            UUID inviteId,
            UUID userId,
            String email,
            Instant at) {
    }

    public record EmailVerificationRequested(
            UUID userId,
            String name,
            String email,
            String token,
            Instant expiresAt,
            String locale) {
    }

    public record EmailVerified(
            UUID userId,
            String name,
            String email,
            String locale) {
    }

    public record MemberJoined(
            UUID organizationId,
            UUID userId,
            String name,
            String email,
            String organizationName,
            UUID inviterId,
            String inviterName,
            String inviterEmail,
            String locale,
            Instant at) {
    }

    public record InviteRenewalRequested(
            UUID organizationId,
            UUID inviteId,
            UUID inviterId,
            String email,
            Instant at) {
    }

    public record EmailChangeRequested(
            UUID userId,
            String name,
            String newEmail,
            String token,
            Instant expiresAt,
            String locale) {
    }

    public record EmailChanged(
            UUID userId,
            String name,
            String previousEmail,
            String newEmail,
            Instant at,
            String locale) {
    }

    public record PasswordResetRequested(
            UUID userId,
            String name,
            String email,
            String token,
            Instant expiresAt,
            String locale) {
    }

    public record PasswordChanged(
            UUID userId,
            String name,
            String email,
            Instant at,
            String locale) {
    }
}
