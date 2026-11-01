package app.nowtask.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InstanceViews {

    private InstanceViews() {
    }

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public record Counts(
            long organizations,
            long organizationsSuspended,
            long users,
            long usersDisabled,
            long instanceAdmins,
            long tasks,
            long activeSessions) {
    }

    public record SignupModeView(String effective, String source) {
    }

    public record Health(String database, String mail, String github) {
    }

    public record Overview(
            String version,
            String schemaVersion,
            Counts counts,
            long signupsLast30Days,
            SignupModeView signupMode,
            Health health) {
    }

    public record OrganizationItem(
            UUID id,
            String name,
            String slug,
            String state,
            String ssoDomain,
            long memberCount,
            long taskCount,
            Instant createdAt,
            Instant suspendedAt) {
    }

    public record UserItem(
            UUID id,
            String name,
            String email,
            String initials,
            String state,
            boolean instanceAdmin,
            boolean emailVerified,
            long organizationCount,
            Instant lastSeenAt) {
    }

    public record PersonRef(UUID id, String name) {
    }

    public record AnnouncementView(String text, String level, Instant until) {
    }

    public record SettingsView(
            String signupMode,
            String effectiveSignupMode,
            String envSignupMode,
            AnnouncementView announcement,
            Instant updatedAt,
            PersonRef updatedBy) {
    }

    public record AuditItem(
            UUID id,
            PersonRef actor,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            Map<String, Object> detail,
            Instant createdAt) {
    }
}
