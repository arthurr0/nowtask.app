package app.nowtask.workspace.web;

import java.util.List;
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
import app.nowtask.shared.TaskQuery;
import app.nowtask.workspace.SavedViewService;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;

@RestController
@RequestMapping("/api/views")
class ViewController {

    private final SavedViewService views;

    ViewController(SavedViewService views) {
        this.views = views;
    }

    @GetMapping
    List<SavedViewView> list() {
        return views.list();
    }

    record ViewBody(String name, TaskQuery query, Boolean shared) {
    }

    record ReorderBody(List<UUID> ids) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedViewView create(@RequestBody ViewBody request) {
        return views.create(request.name(), request.query(), request.shared());
    }

    @PatchMapping("/{id}")
    SavedViewView update(@PathVariable UUID id, @RequestBody ViewBody request) {
        return views.update(id, request.name(), request.query(), request.shared());
    }

    @PostMapping("/reorder")
    List<SavedViewView> reorder(@RequestBody ReorderBody request) {
        views.reorder(request.ids() == null ? List.of() : request.ids());
        return views.list();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        views.delete(id);
        return ResponseEntity.noContent().build();
    }
}
