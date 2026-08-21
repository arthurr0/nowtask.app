package app.nowtask.workspace.api;

import java.time.LocalDate;
import java.util.UUID;
import app.nowtask.shared.Permission;
import app.nowtask.shared.StatusCategory;
import app.nowtask.shared.TaskQuery;

public final class WorkspaceViews {

    private WorkspaceViews() {
    }

    public record ProjectView(UUID id, String name, String code, int position, boolean archived) {
    }

    public record StatusView(
            UUID id,
            String code,
            String label,
            StatusCategory category,
            Integer wipLimit,
            int position,
            String swatch) {
    }

    public record TransitionView(
            UUID id,
            UUID fromStatus,
            UUID toStatus,
            String fromCode,
            String toCode,
            String requirement) {
    }

    public record EpicView(UUID id, String name, UUID projectId) {
    }

    public record CustomFieldView(
            UUID id,
            String name,
            String fieldKey,
            String type,
            String scopeLabel,
            Permission requiredPermission) {
    }

    public record SavedViewView(
            UUID id,
            String name,
            String code,
            TaskQuery query,
            int count,
            boolean shared,
            UUID ownerId) {
    }

    public record MilestoneView(UUID id, String name, LocalDate dueDate) {
    }

    public record TaskFieldSettingView(String fieldKey, UUID projectId, boolean enabled) {
    }

    public record SettingsView(
            String dateFormat,
            String timeFormat,
            int firstDayOfWeek,
            String timeZone,
            String currency,
            boolean allowUserOverride,
            boolean blockDisallowedDrag,
            String currentSprint) {
    }
}
