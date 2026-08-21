package app.nowtask.workspace.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import app.nowtask.workspace.api.WorkspaceViews.CustomFieldView;
import app.nowtask.workspace.api.WorkspaceViews.EpicView;
import app.nowtask.workspace.api.WorkspaceViews.MilestoneView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;
import app.nowtask.workspace.api.WorkspaceViews.SettingsView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;
import app.nowtask.workspace.api.WorkspaceViews.TaskFieldSettingView;
import app.nowtask.workspace.api.WorkspaceViews.TransitionView;

public interface Workspace {

    List<ProjectView> projects();

    ProjectView defaultProject();

    Optional<ProjectView> projectById(UUID id);

    List<StatusView> statuses();

    Optional<StatusView> statusById(UUID id);

    Optional<StatusView> statusByCode(String code);

    List<TransitionView> transitions();

    List<EpicView> epics();

    Optional<EpicView> epicById(UUID id);

    List<CustomFieldView> customFields();

    Optional<CustomFieldView> customFieldByKey(String fieldKey);

    List<SavedViewView> savedViews();

    List<MilestoneView> milestones();

    SettingsView settings();

    List<TaskFieldSettingView> taskFieldSettings();

    Set<String> disabledTaskFields(UUID projectId);

    boolean transitionAllowed(UUID fromStatus, UUID toStatus);
}
