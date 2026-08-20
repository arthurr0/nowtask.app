package app.nowtask.tasks;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.events.TaskCommands;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;

@Service
@Transactional
class TaskCommandAdapter implements TaskCommands {

    private final TaskService tasks;
    private final Workspace workspace;
    private final JdbcClient jdbc;

    TaskCommandAdapter(TaskService tasks, Workspace workspace, JdbcClient jdbc) {
        this.tasks = tasks;
        this.workspace = workspace;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskFacts> facts(String taskKey) {
        return allFacts().stream().filter(entry -> entry.taskKey().equals(taskKey)).findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFacts> allFacts() {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        jdbc.sql("SELECT t.task_key, l.label FROM task_label l JOIN task t ON t.id = l.task_id")
                .query((rs, rowNum) -> Map.entry(rs.getString("task_key"), rs.getString("label")))
                .list()
                .forEach(entry -> labels.computeIfAbsent(entry.getKey(), key -> new java.util.ArrayList<>())
                        .add(entry.getValue()));

        return jdbc.sql("""
                SELECT t.task_key, s.code AS status_code, t.priority, t.assignee_id, t.reviewer_id,
                       t.due_date, t.estimate
                FROM task t
                JOIN status_def s ON s.id = t.status_id
                ORDER BY t.task_key
                """)
                .query((rs, rowNum) -> new TaskFacts(
                        rs.getString("task_key"),
                        rs.getString("status_code"),
                        rs.getString("priority"),
                        labels.getOrDefault(rs.getString("task_key"), List.of()),
                        rs.getObject("assignee_id", UUID.class),
                        rs.getObject("reviewer_id", UUID.class),
                        rs.getObject("due_date", LocalDate.class),
                        (Integer) rs.getObject("estimate")))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setStatus(String taskKey, String statusCode) {
        StatusView status = workspace.statuses().stream()
                .filter(entry -> entry.code().equalsIgnoreCase(statusCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nieznany status: " + statusCode));

        tasks.patch(taskKey, new PatchBody(Map.of("statusId", status.id().toString())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setPriority(String taskKey, String priority) {
        tasks.patch(taskKey, new PatchBody(Map.of("priority", priority)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assign(String taskKey, UUID userId) {
        tasks.patch(taskKey, new PatchBody(mapWithNullable("assigneeId", userId)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setReviewer(String taskKey, UUID userId) {
        tasks.patch(taskKey, new PatchBody(mapWithNullable("reviewerId", userId)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addLabel(String taskKey, String label) {
        tasks.addLabel(taskKey, label);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setDueInDays(String taskKey, int days) {
        tasks.patch(taskKey, new PatchBody(Map.of("dueDate", LocalDate.now().plusDays(days).toString())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addComment(String taskKey, String body) {
        tasks.addComment(taskKey, body);
    }

    private static Map<String, Object> mapWithNullable(String field, UUID value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, value == null ? null : value.toString());
        return body;
    }
}
