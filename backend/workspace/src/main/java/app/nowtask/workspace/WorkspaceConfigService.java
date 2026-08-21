package app.nowtask.workspace;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.Permission;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.StatusCategory;
import app.nowtask.shared.TaskField;
import app.nowtask.shared.events.ConfigEvents;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.CustomFieldView;
import app.nowtask.workspace.api.WorkspaceViews.EpicView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.SettingsView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;
import app.nowtask.workspace.api.WorkspaceViews.TaskFieldSettingView;
import app.nowtask.workspace.api.WorkspaceViews.TransitionView;

@Service
@Transactional
public class WorkspaceConfigService {

    private static final String ALL_PROJECTS = "settings.allProjects";
    private static final String DEFAULT_SWATCH = "var(--c-ink-2)";
    private static final Set<String> FIELD_TYPES = Set.of(
            "text", "number", "currency", "date", "select", "toggle", "person", "url", "relation", "formula");

    private final JdbcClient jdbc;
    private final Workspace workspace;
    private final ApplicationEventPublisher events;

    WorkspaceConfigService(JdbcClient jdbc, Workspace workspace, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.workspace = workspace;
        this.events = events;
    }

    private void announceFlowChange() {
        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        if (context == null || !context.hasOrganization()) {
            return;
        }

        UUID projectId = workspace.projects().stream()
                .filter(project -> !project.archived())
                .findFirst()
                .map(project -> project.id())
                .orElse(null);

        events.publishEvent(new ConfigEvents.StatusFlowChanged(
                context.organizationId(), projectId, context.userId()));
    }

    public StatusView createStatus(String code, String label, String category, Integer wipLimit, Integer position) {
        String statusCode = required(code, "Kod statusu");
        UUID projectId = workspace.defaultProject().id();

        if (workspace.statusByCode(statusCode).isPresent()) {
            throw new RuleViolationException("A status with code " + statusCode + " already exists");
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO status_def (id, project_id, code, label, category, wip_limit, position, swatch)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, projectId, statusCode, required(label, "Status name"),
                        StatusCategory.of(category).code(), wipLimit,
                        position == null ? nextStatusPosition() : position, DEFAULT_SWATCH)
                .update();

        announceFlowChange();
        return requireStatus(id);
    }

    public StatusView updateStatus(UUID id, PatchBody patch) {
        requireStatus(id);

        if (patch.has("code")) {
            String code = required(patch.text("code"), "Kod statusu");
            workspace.statusByCode(code)
                    .filter(other -> !other.id().equals(id))
                    .ifPresent(other -> {
                        throw new RuleViolationException("A status with code " + code + " already exists");
                    });
            update(id, "code", code);
        }
        if (patch.has("label")) {
            update(id, "label", required(patch.text("label"), "Status name"));
        }
        if (patch.has("category")) {
            update(id, "category", StatusCategory.of(patch.text("category")).code());
        }
        if (patch.has("wipLimit")) {
            update(id, "wip_limit", patch.number("wipLimit"));
        }
        if (patch.has("position")) {
            update(id, "position", patch.number("position"));
        }
        if (patch.has("swatch")) {
            update(id, "swatch", patch.text("swatch"));
        }

        announceFlowChange();
        return requireStatus(id);
    }

    public void deleteStatus(UUID id) {
        StatusView status = requireStatus(id);
        int used = jdbc.sql("SELECT count(*) FROM task WHERE status_id = ?").param(id).query(Integer.class).single();

        if (used > 0) {
            throw new RuleViolationException(
                    "Status " + status.label() + " has tasks assigned (" + used + ") and cannot be deleted");
        }

        jdbc.sql("DELETE FROM status_def WHERE id = ?").param(id).update();
        announceFlowChange();
    }

    public List<StatusView> reorderStatuses(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("A status list is required");
        }

        for (int position = 0; position < ids.size(); position++) {
            requireStatus(ids.get(position));
            jdbc.sql("UPDATE status_def SET position = ? WHERE id = ?").params(position, ids.get(position)).update();
        }

        announceFlowChange();
        return workspace.statuses();
    }

    public TransitionView createTransition(UUID fromStatus, UUID toStatus, String requirement) {
        StatusView from = requireStatus(fromStatus);
        StatusView to = requireStatus(toStatus);

        if (from.id().equals(to.id())) {
            throw new RuleViolationException("A transition has to connect two different statuses");
        }
        if (workspace.transitions().stream()
                .anyMatch(t -> t.fromStatus().equals(from.id()) && t.toStatus().equals(to.id()))) {
            throw new RuleViolationException("The transition " + from.label() + " to " + to.label() + " already exists");
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO status_transition (id, from_status, to_status, requirement) VALUES (?, ?, ?, ?)")
                .params(id, from.id(), to.id(), requirement)
                .update();

        return workspace.transitions().stream()
                .filter(transition -> transition.id().equals(id))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Transition", id));
    }

    public void deleteTransition(UUID id) {
        int removed = jdbc.sql("DELETE FROM status_transition WHERE id = ?").param(id).update();
        if (removed == 0) {
            throw NotFoundException.of("Transition", id);
        }
    }

    public CustomFieldView createCustomField(
            String name, String fieldKey, String type, String scopeLabel, String requiredPermission) {
        String key = required(fieldKey, "Field key");

        if (workspace.customFieldByKey(key).isPresent()) {
            throw new RuleViolationException("A field with key " + key + " already exists");
        }

        UUID id = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM custom_field")
                .query(Integer.class)
                .single();

        jdbc.sql("""
                        INSERT INTO custom_field
                            (id, project_id, name, field_key, type, scope_label, required_permission, position)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, projectFor(scopeLabel), required(name, "Field name"), key,
                        fieldType(type), scopeLabel == null ? ALL_PROJECTS : scopeLabel,
                        permission(requiredPermission), position)
                .update();

        return requireCustomField(id);
    }

    public CustomFieldView updateCustomField(UUID id, PatchBody patch) {
        CustomFieldView field = requireCustomField(id);

        if (patch.has("fieldKey") && !field.fieldKey().equals(patch.text("fieldKey"))) {
            throw new RuleViolationException("The field key is immutable");
        }
        if (patch.has("name")) {
            updateField(id, "name", required(patch.text("name"), "Field name"));
        }
        if (patch.has("type")) {
            updateField(id, "type", fieldType(patch.text("type")));
        }
        if (patch.has("scopeLabel")) {
            String scopeLabel = patch.text("scopeLabel");
            updateField(id, "scope_label", scopeLabel == null ? ALL_PROJECTS : scopeLabel);
            updateField(id, "project_id", projectFor(scopeLabel));
        }
        if (patch.has("requiredPermission")) {
            updateField(id, "required_permission", permission(patch.text("requiredPermission")));
        }

        return requireCustomField(id);
    }

    public void deleteCustomField(UUID id) {
        CustomFieldView field = requireCustomField(id);
        jdbc.sql("DELETE FROM task_field_setting WHERE field_key = ?")
                .param(TaskField.customKey(field.fieldKey()))
                .update();
        jdbc.sql("DELETE FROM custom_field WHERE id = ?").param(id).update();
    }

    public ProjectView createProject(String name, String code) {
        String cleanName = required(name, "Project name");
        String cleanCode = projectCode(code, cleanName);

        Integer taken = jdbc.sql("SELECT count(*) FROM project WHERE upper(code) = upper(?)")
                .param(cleanCode)
                .query(Integer.class)
                .single();
        if (taken != null && taken > 0) {
            throw new RuleViolationException("A project with code " + cleanCode + " already exists");
        }

        UUID id = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM project")
                .query(Integer.class)
                .single();

        jdbc.sql("INSERT INTO project (id, name, code, position, archived) VALUES (?, ?, ?, ?, FALSE)")
                .params(id, cleanName, cleanCode, position)
                .update();

        return requireProject(id);
    }

    public ProjectView updateProject(UUID id, PatchBody patch) {
        requireProject(id);

        if (patch.has("name")) {
            jdbc.sql("UPDATE project SET name = ? WHERE id = ?")
                    .params(required(patch.text("name"), "Project name"), id)
                    .update();
        }
        if (patch.has("archived")) {
            boolean archived = Boolean.TRUE.equals(patch.flag("archived"));
            if (archived && activeProjectCount() <= 1) {
                throw new RuleViolationException("This is the only active project, it cannot be archived");
            }
            jdbc.sql("UPDATE project SET archived = ? WHERE id = ?").params(archived, id).update();
        }

        return requireProject(id);
    }

    public void deleteProject(UUID id) {
        requireProject(id);

        Integer tasks = jdbc.sql("SELECT count(*) FROM task WHERE project_id = ?")
                .param(id)
                .query(Integer.class)
                .single();
        if (tasks != null && tasks > 0) {
            throw new RuleViolationException(
                    "The project has " + tasks + " tasks. Move them or archive the project");
        }
        if (activeProjectCount() <= 1) {
            throw new RuleViolationException("This is the only project, it cannot be deleted");
        }

        jdbc.sql("DELETE FROM epic WHERE project_id = ?").param(id).update();
        jdbc.sql("DELETE FROM project WHERE id = ?").param(id).update();
    }

    private ProjectView requireProject(UUID id) {
        return workspace.projectById(id).orElseThrow(() -> NotFoundException.of("Projekt", id));
    }

    private int activeProjectCount() {
        Integer count = jdbc.sql("SELECT count(*) FROM project WHERE NOT archived")
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    private static String projectCode(String code, String name) {
        String source = code == null || code.isBlank() ? name : code;
        String cleaned = source
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (cleaned.isBlank()) {
            throw new RuleViolationException("The project code has to contain letters or digits");
        }
        return cleaned.substring(0, Math.min(6, cleaned.length()));
    }

    public EpicView createEpic(String name, UUID projectId) {
        UUID id = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM epic").query(Integer.class).single();
        UUID project = projectId == null ? workspace.defaultProject().id() : requireProject(projectId).id();

        jdbc.sql("INSERT INTO epic (id, project_id, name, position) VALUES (?, ?, ?, ?)")
                .params(id, project, required(name, "Epic name"), position)
                .update();

        return requireEpic(id);
    }

    public EpicView updateEpic(UUID id, String name) {
        requireEpic(id);
        jdbc.sql("UPDATE epic SET name = ? WHERE id = ?").params(required(name, "Epic name"), id).update();
        return requireEpic(id);
    }

    public void deleteEpic(UUID id) {
        requireEpic(id);
        jdbc.sql("DELETE FROM epic WHERE id = ?").param(id).update();
    }

    public List<TaskFieldSettingView> updateTaskFieldSettings(UUID projectId, Map<String, Object> fields) {
        OrganizationContextHolder.current().require(Permission.FIELDS_MANAGE);

        if (projectId != null) {
            requireProject(projectId);
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Field list: a value is required");
        }

        fields.forEach((key, value) -> applyTaskFieldSetting(projectId, taskFieldKey(key), value));
        return workspace.taskFieldSettings();
    }

    private void applyTaskFieldSetting(UUID projectId, String fieldKey, Object value) {
        if (value == null) {
            if (projectId == null) {
                jdbc.sql("DELETE FROM task_field_setting WHERE project_id IS NULL AND field_key = ?")
                        .param(fieldKey)
                        .update();
            } else {
                jdbc.sql("DELETE FROM task_field_setting WHERE project_id = ? AND field_key = ?")
                        .params(projectId, fieldKey)
                        .update();
            }
            return;
        }

        boolean enabled = value instanceof Boolean logical ? logical : Boolean.parseBoolean(value.toString());
        int updated = projectId == null
                ? jdbc.sql("UPDATE task_field_setting SET enabled = ? WHERE project_id IS NULL AND field_key = ?")
                        .params(enabled, fieldKey)
                        .update()
                : jdbc.sql("UPDATE task_field_setting SET enabled = ? WHERE project_id = ? AND field_key = ?")
                        .params(enabled, projectId, fieldKey)
                        .update();

        if (updated > 0) {
            return;
        }

        if (projectId == null) {
            jdbc.sql("INSERT INTO task_field_setting (id, field_key, enabled) VALUES (?, ?, ?)")
                    .params(UUID.randomUUID(), fieldKey, enabled)
                    .update();
        } else {
            jdbc.sql("INSERT INTO task_field_setting (id, project_id, field_key, enabled) VALUES (?, ?, ?, ?)")
                    .params(UUID.randomUUID(), projectId, fieldKey, enabled)
                    .update();
        }
    }

    private String taskFieldKey(String key) {
        String value = required(key, "Field key");
        if (TaskField.byKey(value).isPresent()) {
            return value;
        }
        if (TaskField.isCustom(value)) {
            String custom = TaskField.customFieldKey(value);
            workspace.customFieldByKey(custom)
                    .orElseThrow(() -> new RuleViolationException("Unknown custom field " + custom));
            return value;
        }
        throw new RuleViolationException("Unknown task field " + value);
    }

    public SettingsView updateSettings(PatchBody patch) {
        if (patch.has("dateFormat")) {
            updateSetting("date_format", required(patch.text("dateFormat"), "Format daty"));
        }
        if (patch.has("timeFormat")) {
            updateSetting("time_format", required(patch.text("timeFormat"), "Format godziny"));
        }
        if (patch.has("firstDayOfWeek")) {
            updateSetting("first_day_of_week", firstDayOfWeek(patch.number("firstDayOfWeek")));
        }
        if (patch.has("timeZone")) {
            updateSetting("time_zone", timeZone(patch.text("timeZone")));
        }
        if (patch.has("currency")) {
            updateSetting("currency", required(patch.text("currency"), "Waluta"));
        }
        if (patch.has("currentSprint")) {
            String sprint = patch.text("currentSprint");
            updateSetting("current_sprint", sprint == null || sprint.isBlank() ? null : sprint.trim());
        }
        if (patch.has("allowUserOverride")) {
            updateSetting("allow_user_override", flag(patch, "allowUserOverride"));
        }
        if (patch.has("blockDisallowedDrag")) {
            updateSetting("block_disallowed_drag", flag(patch, "blockDisallowedDrag"));
        }

        return workspace.settings();
    }

    private void update(UUID id, String column, Object value) {
        jdbc.sql("UPDATE status_def SET " + column + " = ? WHERE id = ?").params(value, id).update();
    }

    private void updateField(UUID id, String column, Object value) {
        jdbc.sql("UPDATE custom_field SET " + column + " = ? WHERE id = ?").params(value, id).update();
    }

    private void updateSetting(String column, Object value) {
        jdbc.sql("UPDATE workspace_settings SET " + column + " = ?").param(value).update();
    }

    private int nextStatusPosition() {
        return jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM status_def").query(Integer.class).single();
    }

    private UUID projectFor(String scopeLabel) {
        return scopeLabel == null || scopeLabel.isBlank() || ALL_PROJECTS.equals(scopeLabel)
                ? null
                : workspace.defaultProject().id();
    }

    private String fieldType(String type) {
        String value = required(type, "Typ pola");
        if (!FIELD_TYPES.contains(value)) {
            throw new IllegalArgumentException("Nieznany typ pola: " + value);
        }
        return value;
    }

    private String permission(String code) {
        return code == null || code.isBlank() ? null : Permission.of(code).code();
    }

    private boolean flag(PatchBody patch, String field) {
        Boolean value = patch.flag(field);
        if (value == null) {
            throw new IllegalArgumentException("Field " + field + " requires a boolean value");
        }
        return value;
    }

    private int firstDayOfWeek(Integer day) {
        if (day == null || day < 1 || day > 7) {
            throw new IllegalArgumentException("The first day of the week has to be a number from 1 to 7");
        }
        return day;
    }

    private String timeZone(String zone) {
        String value = required(zone, "Strefa czasowa");
        if (!java.time.ZoneId.getAvailableZoneIds().contains(value)) {
            throw new IllegalArgumentException("Nieznana strefa czasowa: " + value);
        }
        return value;
    }

    private String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + ": a value is required");
        }
        return value.trim();
    }

    private StatusView requireStatus(UUID id) {
        return workspace.statusById(id).orElseThrow(() -> NotFoundException.of("Status", id));
    }

    private CustomFieldView requireCustomField(UUID id) {
        return workspace.customFields().stream()
                .filter(field -> field.id().equals(id))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Custom field", id));
    }

    private EpicView requireEpic(UUID id) {
        return workspace.epicById(id).orElseThrow(() -> NotFoundException.of("Epik", id));
    }
}
