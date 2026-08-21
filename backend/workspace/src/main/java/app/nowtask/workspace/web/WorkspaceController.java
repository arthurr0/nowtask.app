package app.nowtask.workspace.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.shared.PatchBody;
import app.nowtask.workspace.WorkspaceConfigService;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.CustomFieldView;
import app.nowtask.workspace.api.WorkspaceViews.EpicView;
import app.nowtask.workspace.api.WorkspaceViews.MilestoneView;
import app.nowtask.workspace.api.WorkspaceViews.SettingsView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;
import app.nowtask.workspace.api.WorkspaceViews.TransitionView;

@RestController
@RequestMapping("/api/workspace")
class WorkspaceController {

    private final Workspace workspace;
    private final WorkspaceConfigService config;

    WorkspaceController(Workspace workspace, WorkspaceConfigService config) {
        this.workspace = workspace;
        this.config = config;
    }

    @GetMapping("/projects")
    List<ProjectView> projects() {
        return workspace.projects();
    }

    record NewProject(String name, String code) {
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectView createProject(@RequestBody NewProject request) {
        return config.createProject(request.name(), request.code());
    }

    @PatchMapping("/projects/{id}")
    ProjectView updateProject(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return config.updateProject(id, new PatchBody(body));
    }

    @DeleteMapping("/projects/{id}")
    ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        config.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statuses")
    List<StatusView> statuses() {
        return workspace.statuses();
    }

    record NewStatus(String code, String label, String category, Integer wipLimit, Integer position) {
    }

    @PostMapping("/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    StatusView createStatus(@RequestBody NewStatus request) {
        return config.createStatus(
                request.code(), request.label(), request.category(), request.wipLimit(), request.position());
    }

    @PatchMapping("/statuses/{id}")
    StatusView updateStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return config.updateStatus(id, new PatchBody(body));
    }

    @DeleteMapping("/statuses/{id}")
    ResponseEntity<Void> deleteStatus(@PathVariable UUID id) {
        config.deleteStatus(id);
        return ResponseEntity.noContent().build();
    }

    record StatusOrder(List<UUID> ids) {
    }

    @PostMapping("/statuses/reorder")
    List<StatusView> reorderStatuses(@RequestBody StatusOrder request) {
        return config.reorderStatuses(request.ids());
    }

    @GetMapping("/transitions")
    List<TransitionView> transitions() {
        return workspace.transitions();
    }

    record NewTransition(UUID fromStatus, UUID toStatus, String requirement) {
    }

    @PostMapping("/transitions")
    @ResponseStatus(HttpStatus.CREATED)
    TransitionView createTransition(@RequestBody NewTransition request) {
        return config.createTransition(request.fromStatus(), request.toStatus(), request.requirement());
    }

    @DeleteMapping("/transitions/{id}")
    ResponseEntity<Void> deleteTransition(@PathVariable UUID id) {
        config.deleteTransition(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/custom-fields")
    List<CustomFieldView> customFields() {
        return workspace.customFields();
    }

    record NewCustomField(String name, String fieldKey, String type, String scopeLabel, String requiredPermission) {
    }

    @PostMapping("/custom-fields")
    @ResponseStatus(HttpStatus.CREATED)
    CustomFieldView createCustomField(@RequestBody NewCustomField request) {
        return config.createCustomField(
                request.name(), request.fieldKey(), request.type(), request.scopeLabel(), request.requiredPermission());
    }

    @PatchMapping("/custom-fields/{id}")
    CustomFieldView updateCustomField(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return config.updateCustomField(id, new PatchBody(body));
    }

    @DeleteMapping("/custom-fields/{id}")
    ResponseEntity<Void> deleteCustomField(@PathVariable UUID id) {
        config.deleteCustomField(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/epics")
    List<EpicView> epics() {
        return workspace.epics();
    }

    record NewEpic(String name, UUID projectId) {
    }

    @PostMapping("/epics")
    @ResponseStatus(HttpStatus.CREATED)
    EpicView createEpic(@RequestBody NewEpic request) {
        return config.createEpic(request.name(), request.projectId());
    }

    @PatchMapping("/epics/{id}")
    EpicView updateEpic(@PathVariable UUID id, @RequestBody NewEpic request) {
        return config.updateEpic(id, request.name());
    }

    @DeleteMapping("/epics/{id}")
    ResponseEntity<Void> deleteEpic(@PathVariable UUID id) {
        config.deleteEpic(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/milestones")
    List<MilestoneView> milestones() {
        return workspace.milestones();
    }

    @GetMapping("/settings")
    SettingsView settings() {
        return workspace.settings();
    }

    @PatchMapping("/settings")
    SettingsView updateSettings(@RequestBody Map<String, Object> body) {
        return config.updateSettings(new PatchBody(body));
    }
}
