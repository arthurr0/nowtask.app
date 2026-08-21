package app.nowtask.workspace;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.Permission;
import app.nowtask.shared.StatusCategory;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.CustomFieldView;
import app.nowtask.workspace.api.WorkspaceViews.EpicView;
import app.nowtask.workspace.api.WorkspaceViews.MilestoneView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;
import app.nowtask.workspace.api.WorkspaceViews.SettingsView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;
import app.nowtask.workspace.api.WorkspaceViews.TaskFieldSettingView;
import app.nowtask.workspace.api.WorkspaceViews.TaskViewSettingView;
import app.nowtask.workspace.api.WorkspaceViews.TransitionView;

@Service
@Transactional(readOnly = true)
class WorkspaceService implements Workspace {

    private final JdbcClient jdbc;
    private final SavedViewService savedViews;

    WorkspaceService(JdbcClient jdbc, SavedViewService savedViews) {
        this.jdbc = jdbc;
        this.savedViews = savedViews;
    }

    @Override
    public List<ProjectView> projects() {
        return jdbc.sql("SELECT id, name, code, position, archived FROM project ORDER BY position, name")
                .query((rs, rowNum) -> new ProjectView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getInt("position"),
                        rs.getBoolean("archived")))
                .list();
    }

    @Override
    public ProjectView defaultProject() {
        return projects().stream()
                .filter(project -> !project.archived())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No project defined"));
    }

    @Override
    public Optional<ProjectView> projectById(UUID id) {
        return projects().stream().filter(project -> project.id().equals(id)).findFirst();
    }

    @Override
    public List<StatusView> statuses() {
        return jdbc.sql("""
                        SELECT id, code, label, category, wip_limit, position, swatch
                        FROM status_def
                        ORDER BY position
                        """)
                .query(WorkspaceService::toStatus)
                .list();
    }

    @Override
    public Optional<StatusView> statusById(UUID id) {
        return statuses().stream().filter(status -> status.id().equals(id)).findFirst();
    }

    @Override
    public Optional<StatusView> statusByCode(String code) {
        return statuses().stream().filter(status -> status.code().equals(code)).findFirst();
    }

    @Override
    public List<TransitionView> transitions() {
        return jdbc.sql("""
                        SELECT t.id, t.from_status, t.to_status, f.code AS from_code, s.code AS to_code, t.requirement
                        FROM status_transition t
                        JOIN status_def f ON f.id = t.from_status
                        JOIN status_def s ON s.id = t.to_status
                        ORDER BY f.position, s.position
                        """)
                .query((rs, rowNum) -> new TransitionView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("from_status", UUID.class),
                        rs.getObject("to_status", UUID.class),
                        rs.getString("from_code"),
                        rs.getString("to_code"),
                        rs.getString("requirement")))
                .list();
    }

    @Override
    public List<EpicView> epics() {
        return jdbc.sql("SELECT id, name, project_id FROM epic ORDER BY position, name")
                .query((rs, rowNum) -> new EpicView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("project_id", UUID.class)))
                .list();
    }

    @Override
    public Optional<EpicView> epicById(UUID id) {
        return epics().stream().filter(epic -> epic.id().equals(id)).findFirst();
    }

    @Override
    public List<CustomFieldView> customFields() {
        return jdbc.sql("""
                        SELECT id, name, field_key, type, scope_label, required_permission
                        FROM custom_field
                        ORDER BY position
                        """)
                .query(WorkspaceService::toCustomField)
                .list();
    }

    @Override
    public Optional<CustomFieldView> customFieldByKey(String fieldKey) {
        return customFields().stream().filter(field -> field.fieldKey().equals(fieldKey)).findFirst();
    }

    @Override
    public List<SavedViewView> savedViews() {
        return savedViews.list();
    }

    @Override
    public List<MilestoneView> milestones() {
        return jdbc.sql("SELECT id, name, due_date FROM milestone ORDER BY due_date")
                .query((rs, rowNum) -> new MilestoneView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("due_date", LocalDate.class)))
                .list();
    }

    @Override
    public SettingsView settings() {
        return jdbc.sql("""
                        SELECT date_format, time_format, first_day_of_week, time_zone,
                               currency, allow_user_override, block_disallowed_drag, current_sprint
                        FROM workspace_settings
                        ORDER BY id
                        LIMIT 1
                        """)
                .query((rs, rowNum) -> new SettingsView(
                        rs.getString("date_format"),
                        rs.getString("time_format"),
                        rs.getInt("first_day_of_week"),
                        rs.getString("time_zone"),
                        rs.getString("currency"),
                        rs.getBoolean("allow_user_override"),
                        rs.getBoolean("block_disallowed_drag"),
                        rs.getString("current_sprint")))
                .optional()
                .orElseThrow(() -> new NotFoundException("No workspace settings"));
    }

    @Override
    public List<TaskFieldSettingView> taskFieldSettings() {
        return jdbc.sql("SELECT field_key, project_id, enabled FROM task_field_setting ORDER BY project_id, field_key")
                .query((rs, rowNum) -> new TaskFieldSettingView(
                        rs.getString("field_key"),
                        rs.getObject("project_id", UUID.class),
                        rs.getBoolean("enabled")))
                .list();
    }

    @Override
    public Set<String> disabledTaskFields(UUID projectId) {
        Map<String, Boolean> effective = new HashMap<>();
        for (TaskFieldSettingView setting : taskFieldSettings()) {
            if (setting.projectId() == null) {
                effective.putIfAbsent(setting.fieldKey(), setting.enabled());
            } else if (setting.projectId().equals(projectId)) {
                effective.put(setting.fieldKey(), setting.enabled());
            }
        }
        return effective.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public List<TaskViewSettingView> taskViewSettings() {
        return jdbc.sql("SELECT view_code, project_id, enabled FROM task_view_setting ORDER BY project_id, view_code")
                .query((rs, rowNum) -> new TaskViewSettingView(
                        rs.getString("view_code"),
                        rs.getObject("project_id", UUID.class),
                        rs.getBoolean("enabled")))
                .list();
    }

    @Override
    public Set<String> disabledTaskViews(UUID projectId) {
        Map<String, Boolean> effective = new HashMap<>();
        for (TaskViewSettingView setting : taskViewSettings()) {
            if (setting.projectId() == null) {
                effective.putIfAbsent(setting.viewCode(), setting.enabled());
            } else if (setting.projectId().equals(projectId)) {
                effective.put(setting.viewCode(), setting.enabled());
            }
        }
        return effective.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean transitionAllowed(UUID fromStatus, UUID toStatus) {
        if (fromStatus.equals(toStatus)) {
            return true;
        }
        return transitions().stream()
                .anyMatch(t -> t.fromStatus().equals(fromStatus) && t.toStatus().equals(toStatus));
    }

    static StatusView toStatus(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new StatusView(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("label"),
                StatusCategory.of(rs.getString("category")),
                rs.getObject("wip_limit") == null ? null : rs.getInt("wip_limit"),
                rs.getInt("position"),
                rs.getString("swatch"));
    }

    static CustomFieldView toCustomField(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CustomFieldView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("field_key"),
                rs.getString("type"),
                rs.getString("scope_label"),
                rs.getString("required_permission") == null
                        ? null
                        : Permission.of(rs.getString("required_permission")));
    }
}
