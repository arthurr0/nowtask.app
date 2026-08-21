package app.nowtask.tasks.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.TaskQuery;
import app.nowtask.tasks.TaskQueryService;
import app.nowtask.tasks.TaskService;
import app.nowtask.tasks.api.TaskViews.CommentView;
import app.nowtask.tasks.api.TaskViews.HistoryView;
import app.nowtask.tasks.api.TaskViews.RelationView;
import app.nowtask.tasks.api.TaskViews.SubtaskView;
import app.nowtask.tasks.api.TaskViews.TaskDetail;
import app.nowtask.tasks.api.TaskViews.TaskPage;
import app.nowtask.tasks.api.TaskViews.TaskSummary;

@RestController
@RequestMapping("/api/tasks")
class TaskController {

    private final TaskService tasks;
    private final TaskQueryService queries;
    private final UserDirectory users;

    TaskController(TaskService tasks, TaskQueryService queries, UserDirectory users) {
        this.tasks = tasks;
        this.queries = queries;
        this.users = users;
    }

    @GetMapping
    TaskPage list(@ModelAttribute TaskQuery query) {
        return queries.page(query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskSummary create(@RequestBody TaskService.NewTask request) {
        return tasks.create(request);
    }

    @GetMapping("/{key}")
    TaskDetail detail(@PathVariable String key) {
        return tasks.detail(key);
    }

    @PatchMapping("/{key}")
    TaskSummary patch(@PathVariable String key, @RequestBody Map<String, Object> body) {
        return tasks.patch(key, new PatchBody(body));
    }

    @DeleteMapping("/{key}")
    ResponseEntity<Void> delete(@PathVariable String key) {
        tasks.delete(key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{key}/comments")
    List<CommentView> comments(@PathVariable String key) {
        return tasks.comments(key);
    }

    record NewComment(String body) {
    }

    @PostMapping("/{key}/comments")
    CommentView addComment(@PathVariable String key, @RequestBody NewComment comment) {
        return tasks.addComment(key, comment.body());
    }

    @GetMapping("/{key}/history")
    List<HistoryView> history(@PathVariable String key) {
        return tasks.history(key);
    }

    record NewSubtask(String title, UUID assigneeId) {
    }

    @PostMapping("/{key}/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    SubtaskView addSubtask(@PathVariable String key, @RequestBody NewSubtask request) {
        return tasks.addSubtask(key, request.title(), request.assigneeId());
    }

    @PatchMapping("/{key}/subtasks/{subtaskId}")
    SubtaskView updateSubtask(
            @PathVariable String key, @PathVariable UUID subtaskId, @RequestBody Map<String, Object> body) {
        return tasks.updateSubtask(key, subtaskId, new PatchBody(body));
    }

    @DeleteMapping("/{key}/subtasks/{subtaskId}")
    ResponseEntity<Void> deleteSubtask(@PathVariable String key, @PathVariable UUID subtaskId) {
        tasks.deleteSubtask(key, subtaskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{key}/subtasks/{subtaskId}/toggle")
    TaskSummary toggleSubtask(@PathVariable String key, @PathVariable UUID subtaskId) {
        return tasks.toggleSubtask(key, subtaskId);
    }

    record NewLabel(String label) {
    }

    @PostMapping("/{key}/labels")
    List<String> addLabel(@PathVariable String key, @RequestBody NewLabel request) {
        return tasks.addLabel(key, request.label());
    }

    @DeleteMapping("/{key}/labels/{label}")
    List<String> removeLabel(@PathVariable String key, @PathVariable String label) {
        return tasks.removeLabel(key, label);
    }

    record NewRelation(String kind, String taskKey) {
    }

    @PostMapping("/{key}/relations")
    List<RelationView> addRelation(@PathVariable String key, @RequestBody NewRelation request) {
        return tasks.addRelation(key, request.kind(), request.taskKey());
    }

    @DeleteMapping("/{key}/relations/{kind}/{taskKey}")
    ResponseEntity<Void> removeRelation(
            @PathVariable String key, @PathVariable String kind, @PathVariable String taskKey) {
        tasks.removeRelation(key, kind, taskKey);
        return ResponseEntity.noContent().build();
    }

    record CustomValue(Object value) {
    }

    @PutMapping("/{key}/custom/{fieldKey}")
    TaskDetail setCustomValue(
            @PathVariable String key, @PathVariable String fieldKey, @RequestBody CustomValue request) {
        return tasks.setCustomValue(key, fieldKey, request.value());
    }

    record Watching(boolean watching) {
    }

    @PostMapping("/{key}/watch")
    Watching watch(@PathVariable String key) {
        return new Watching(tasks.toggleWatch(key));
    }

    record BulkAssign(List<String> keys, UUID assigneeId) {
    }

    @PostMapping("/bulk/assign")
    ResponseEntity<Void> assign(@RequestBody BulkAssign request) {
        tasks.assign(request.keys(), request.assigneeId());
        return ResponseEntity.noContent().build();
    }

    record BulkStatus(List<String> keys, UUID statusId) {
    }

    @PostMapping("/bulk/status")
    ResponseEntity<Void> status(@RequestBody BulkStatus request) {
        tasks.changeStatus(request.keys(), request.statusId());
        return ResponseEntity.noContent().build();
    }

    record BulkDelete(List<String> keys) {
    }

    @PostMapping("/bulk/delete")
    ResponseEntity<Void> delete(@RequestBody BulkDelete request) {
        tasks.delete(request.keys());
        return ResponseEntity.noContent().build();
    }
}
