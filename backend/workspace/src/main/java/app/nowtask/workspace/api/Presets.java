package app.nowtask.workspace.api;

import java.util.List;
import app.nowtask.workspace.api.PresetViews.PresetDetailView;
import app.nowtask.workspace.api.PresetViews.PresetSummaryView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;

public interface Presets {

    List<PresetSummaryView> catalog();

    PresetDetailView detail(String code);

    ProjectView createProject(String name, String code, String presetCode);
}
