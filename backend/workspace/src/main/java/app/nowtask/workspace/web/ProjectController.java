package app.nowtask.workspace.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.Permission;
import app.nowtask.workspace.api.Presets;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final Workspace workspace;
    private final Presets presets;

    ProjectController(Workspace workspace, Presets presets) {
        this.workspace = workspace;
        this.presets = presets;
    }

    @GetMapping
    List<ProjectView> projects() {
        return workspace.projects();
    }

    record NewProject(String name, String code, String presetCode) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectView create(@RequestBody NewProject request) {
        OrganizationContextHolder.current().require(Permission.PROJECTS_MANAGE);
        return presets.createProject(request.name(), request.code(), request.presetCode());
    }
}
