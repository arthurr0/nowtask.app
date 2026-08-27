package app.nowtask.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.ActorContext;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.Priority;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.StatusCategory;
import app.nowtask.shared.TaskField;
import app.nowtask.shared.TaskQuery;
import app.nowtask.shared.events.RealtimeEvents;
import app.nowtask.shared.events.TaskEvents;
import app.nowtask.tasks.api.TaskViews.CommentView;
import app.nowtask.tasks.api.TaskViews.HistoryView;
import app.nowtask.tasks.api.TaskViews.RelationView;
import app.nowtask.tasks.api.TaskViews.ScheduledTask;
import app.nowtask.tasks.api.TaskViews.SubtaskView;
import app.nowtask.tasks.api.TaskViews.TaskDetail;
import app.nowtask.tasks.api.TaskViews.TaskSummary;
import app.nowtask.tasks.api.Tasks;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.CustomFieldView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;

@Service
@Transactional
public class TaskService implements Tasks {

    private static final List<String> RELATION_KINDS = List.of("blocks", "relates");

    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final TaskCommentRepository comments;
    private final TaskHistoryRepository history;
    private final TaskWatcherRepository watchers;
    private final TaskQueryService queries;
    private final TaskKeyAllocator keys;
    private final Workspace workspace;
    private final UserDirectory users;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    TaskService(
            TaskRepository tasks,
            SubtaskRepository subtasks,
            TaskCommentRepository comments,
            TaskHistoryRepository history,
            TaskWatcherRepository watchers,
            TaskQueryService queries,
            TaskKeyAllocator keys,
            Workspace workspace,
            UserDirectory users,
            JdbcClient jdbc,
            ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.comments = comments;
        this.history = history;
        this.watchers = watchers;
        this.queries = queries;
        this.keys = keys;
        this.workspace = workspace;
        this.users = users;
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummary> currentSprint() {
        return queries.page(TaskQuery.empty()).items();
    }

    @Transactional(readOnly = true)
    public TaskDetail detail(String key) {
        Task task = require(key);
        List<SubtaskView> subtaskViews = subtaskViews(task);

        return new TaskDetail(
                toSummary(task, subtaskViews.size(), (int) subtaskViews.stream().filter(SubtaskView::done).count(),
                        (int) comments.countByTaskId(task.getId())),
                task.getDescription(),
                task.getReviewerId(),
                task.getAttachmentCount(),
                watchers.countByTaskId(task.getId()),
                watchers.findByTaskIdAndUserId(task.getId(), users.currentUser().id()).isPresent(),
                visibleCustomFields(task),
                subtaskViews,
                relationsOf(task.getId()));
    }

    @Transactional(readOnly = true)
    public List<CommentView> comments(String key) {
        Task task = require(key);
        return comments.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(c -> new CommentView(c.getId(), c.getAuthorId(), c.getBody(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(String key) {
        Task task = require(key);
        return history.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                .map(h -> new HistoryView(
                        h.getId(), h.getField(), h.getOldValue(), h.getNewValue(),
                        h.getActorId(), h.getRuleName(), h.getCreatedAt()))
                .toList();
    }

    public CommentView addComment(String key, String body) {
        Task task = require(key);
        UserView author = users.currentUser();
        TaskComment comment = new TaskComment(
                UUID.randomUUID(), task.getId(), author.id(), required(body, "Comment body"), Instant.now());
        comments.save(comment);
        changed(task.getKey(), "comment");
        return new CommentView(comment.getId(), comment.getAuthorId(), comment.getBody(), comment.getCreatedAt());
    }

    public TaskSummary create(NewTask request) {
        UserView actor = users.currentUser();
        ProjectView project = request.projectId() == null
                ? workspace.defaultProject()
                : workspace.projectById(request.projectId())
                        .orElseThrow(() -> NotFoundException.of("Projekt", request.projectId()));
        StatusView status = request.statusId() == null
                ? firstStatus()
                : requireStatus(request.statusId());

        Set<String> disabled = workspace.disabledTaskFields(project.id());
        requireEnabled(disabled, TaskField.DESCRIPTION, request.description());
        requireEnabled(disabled, TaskField.PRIORITY, request.priority());
        requireEnabled(disabled, TaskField.ASSIGNEE, request.assigneeId());
        requireEnabled(disabled, TaskField.REVIEWER, request.reviewerId());
        requireEnabled(disabled, TaskField.DUE_DATE, request.dueDate());
        requireEnabled(disabled, TaskField.ESTIMATE, request.estimate());
        requireEnabled(disabled, TaskField.EPIC, request.epicId());
        requireEnabled(disabled, TaskField.LABELS, request.labels());
        requireEnabled(disabled, TaskField.SPRINT, request.sprintCode());

        Task task = new Task(
                UUID.randomUUID(),
                project.id(),
                keys.nextKey(project),
                required(request.title(), "Task title"),
                status.id(),
                sprintOf(request.sprintCode()));

        task.setDescription(request.description() == null ? "" : request.description());
        if (request.priority() != null) {
            task.setPriority(Priority.of(request.priority()));
        }
        task.setAssigneeId(knownUser(request.assigneeId()));
        task.setReviewerId(knownUser(request.reviewerId()));
        task.setDueDate(parseDate(request.dueDate(), "dueDate"));
        task.setEstimate(request.estimate());
        task.setEpicId(knownEpic(request.epicId()));
        if (request.labels() != null) {
            task.getLabels().addAll(request.labels());
        }
        if (request.custom() != null) {
            request.custom().keySet().forEach(fieldKey -> requireCustomEnabled(disabled, fieldKey));
            task.setCustom(knownCustomFields(request.custom()));
        }
        if (status.category() == StatusCategory.IN_FLIGHT) {
            task.setStartedAt(Instant.now());
        }

        tasks.save(task);
        record(task, "created", null, task.getTitle(), actor.id());
        events.publishEvent(new TaskEvents.TaskCreated(
                task.getKey(), task.getTitle(), actor.id(), Instant.now(), ActorContext.currentLabel()));

        if (task.getAssigneeId() != null) {
            events.publishEvent(
                    new TaskEvents.TaskAssigned(task.getKey(), task.getAssigneeId(), actor.id(), Instant.now(),
                            ActorContext.currentLabel()));
        }

        changed(task.getKey(), "created");
        return summaryOf(task);
    }

    public TaskSummary patch(String key, PatchBody patch) {
        Task task = require(key);
        UserView actor = users.currentUser();
        Set<String> disabled = workspace.disabledTaskFields(task.getProjectId());

        requireEnabled(disabled, patch, "description", TaskField.DESCRIPTION);
        requireEnabled(disabled, patch, "priority", TaskField.PRIORITY);
        requireEnabled(disabled, patch, "assigneeId", TaskField.ASSIGNEE);
        requireEnabled(disabled, patch, "reviewerId", TaskField.REVIEWER);
        requireEnabled(disabled, patch, "dueDate", TaskField.DUE_DATE);
        requireEnabled(disabled, patch, "estimate", TaskField.ESTIMATE);
        requireEnabled(disabled, patch, "epicId", TaskField.EPIC);
        requireEnabled(disabled, patch, "sprintCode", TaskField.SPRINT);

        boolean contentChanged = false;

        if (patch.has("title")) {
            String title = required(patch.text("title"), "Task title");
            record(task, "title", task.getTitle(), title, actor.id());
            task.setTitle(title);
            contentChanged = true;
        }
        if (patch.has("description")) {
            task.setDescription(patch.text("description") == null ? "" : patch.text("description"));
            contentChanged = true;
        }
        if (patch.has("priority")) {
            applyPriority(task, Priority.of(required(patch.text("priority"), "Priorytet")), actor.id());
        }
        if (patch.has("estimate")) {
            record(task, "estimate", String.valueOf(task.getEstimate()), String.valueOf(patch.number("estimate")),
                    actor.id());
            task.setEstimate(patch.number("estimate"));
        }
        if (patch.has("dueDate")) {
            task.setDueDate(patch.date("dueDate"));
        }
        if (patch.has("startDate")) {
            task.setStartDate(patch.date("startDate"));
        }
        if (patch.has("endDate")) {
            task.setEndDate(patch.date("endDate"));
        }
        if (patch.has("reviewerId")) {
            task.setReviewerId(knownUser(patch.id("reviewerId")));
        }
        if (patch.has("sprintCode")) {
            String sprint = patch.text("sprintCode");
            record(task, "sprint", task.getSprintCode(), sprint, actor.id());
            task.setSprintCode(sprint == null || sprint.isBlank() ? null : sprint.trim());
        }
        if (patch.has("epicId")) {
            task.setEpicId(knownEpic(patch.id("epicId")));
        }
        if (patch.has("assigneeId")) {
            applyAssignee(task, patch.id("assigneeId"), actor.id());
        }
        if (patch.has("statusId")) {
            UUID statusId = patch.id("statusId");
            if (statusId == null) {
                throw new IllegalArgumentException("The task status is required");
            }
            applyStatus(task, statusId, actor.id());
        }

        task.touch();

        if (contentChanged) {
            events.publishEvent(new TaskEvents.TaskContentChanged(
                    task.getKey(), task.getTitle(), task.getDescription(), actor.id(), Instant.now(),
                    ActorContext.currentLabel()));
        }

        changed(task.getKey(), "updated");
        return summaryOf(task);
    }

    public void assign(List<String> keys, UUID assigneeId) {
        UserView actor = users.currentUser();
        for (Task task : tasks.findByKeyIn(keys)) {
            applyAssignee(task, assigneeId, actor.id());
            task.touch();
            changed(task.getKey(), "updated");
        }
    }

    public void changeStatus(List<String> keys, UUID statusId) {
        UserView actor = users.currentUser();
        for (Task task : tasks.findByKeyIn(keys)) {
            applyStatus(task, statusId, actor.id());
            task.touch();
            changed(task.getKey(), "updated");
        }
    }

    public void delete(String key) {
        remove(List.of(require(key)));
    }

    public void delete(List<String> keys) {
        remove(tasks.findByKeyIn(keys));
    }

    public SubtaskView addSubtask(String key, String title, UUID assigneeId) {
        Task task = require(key);
        int position = subtasks.findByTaskIdOrderByPositionAsc(task.getId()).size();
        Subtask subtask = new Subtask(
                UUID.randomUUID(), task.getId(), required(title, "Subtask title"), knownUser(assigneeId), position);

        subtasks.save(subtask);
        recalculateProgress(task);
        changed(task.getKey(), "subtask");
        return toView(subtask);
    }

    public SubtaskView updateSubtask(String key, UUID subtaskId, PatchBody patch) {
        Task task = require(key);
        Subtask subtask = requireSubtask(task, subtaskId);

        if (patch.has("title")) {
            subtask.setTitle(required(patch.text("title"), "Subtask title"));
        }
        if (patch.has("assigneeId")) {
            subtask.setAssigneeId(knownUser(patch.id("assigneeId")));
        }
        if (patch.has("done")) {
            Boolean done = patch.flag("done");
            if (done == null) {
                throw new IllegalArgumentException("The done field requires a boolean value");
            }
            markDone(task, subtask, done);
        }

        recalculateProgress(task);
        changed(task.getKey(), "subtask");
        return toView(subtask);
    }

    public void deleteSubtask(String key, UUID subtaskId) {
        Task task = require(key);
        subtasks.delete(requireSubtask(task, subtaskId));
        recalculateProgress(task);
        changed(task.getKey(), "subtask");
    }

    public TaskSummary toggleSubtask(String key, UUID subtaskId) {
        Task task = require(key);
        Subtask subtask = requireSubtask(task, subtaskId);

        markDone(task, subtask, !subtask.isDone());
        recalculateProgress(task);
        changed(task.getKey(), "subtask");
        return summaryOf(task);
    }

    public List<String> addLabel(String key, String label) {
        Task task = require(key);
        String value = required(label, "Etykieta").toLowerCase();

        requireEnabled(workspace.disabledTaskFields(task.getProjectId()), TaskField.LABELS, value);

        if (task.getLabels().add(value)) {
            task.touch();
            record(task, "label", null, value, users.currentUser().id());
            events.publishEvent(
                    new TaskEvents.TaskLabelAdded(task.getKey(), value, users.currentUser().id(), Instant.now(),
                            ActorContext.currentLabel()));
            changed(task.getKey(), "labels");
        }

        return List.copyOf(task.getLabels());
    }

    public List<String> removeLabel(String key, String label) {
        Task task = require(key);

        if (task.getLabels().remove(label)) {
            task.touch();
            record(task, "label", label, null, users.currentUser().id());
            changed(task.getKey(), "labels");
        }

        return List.copyOf(task.getLabels());
    }

    public List<RelationView> addRelation(String key, String kind, String otherKey) {
        Task task = require(key);
        Task other = require(otherKey);
        String relation = relationKind(kind);

        if (task.getId().equals(other.getId())) {
            throw new RuleViolationException("A task cannot point at itself");
        }

        jdbc.sql("""
                        INSERT INTO task_relation (from_task, to_task, kind)
                        VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """)
                .params(task.getId(), other.getId(), relation)
                .update();

        record(task, "relation", null, relation + ":" + other.getKey(), users.currentUser().id());
        changed(task.getKey(), "relations");
        changed(other.getKey(), "relations");
        return relationsOf(task.getId());
    }

    public List<RelationView> removeRelation(String key, String kind, String otherKey) {
        Task task = require(key);
        Task other = require(otherKey);

        int removed = jdbc.sql("DELETE FROM task_relation WHERE from_task = ? AND to_task = ? AND kind = ?")
                .params(task.getId(), other.getId(), relationKind(kind))
                .update();

        if (removed == 0) {
            throw new NotFoundException("The " + kind + " link to " + otherKey + " does not exist");
        }

        changed(task.getKey(), "relations");
        changed(other.getKey(), "relations");
        return relationsOf(task.getId());
    }

    public TaskDetail setCustomValue(String key, String fieldKey, Object value) {
        Task task = require(key);
        CustomFieldView field = workspace.customFieldByKey(fieldKey)
                .orElseThrow(() -> NotFoundException.of("Custom field", fieldKey));

        requireCustomEnabled(workspace.disabledTaskFields(task.getProjectId()), fieldKey);

        if (field.requiredPermission() != null
                && !OrganizationContextHolder.current().can(field.requiredPermission())) {
            throw new RuleViolationException("Field " + field.name() + " is not available to this role");
        }

        Map<String, Object> custom = new LinkedHashMap<>(task.getCustom());
        if (value == null) {
            custom.remove(fieldKey);
        } else {
            custom.put(fieldKey, value);
        }

        task.setCustom(custom);
        task.touch();
        record(task, "custom." + fieldKey, null, value == null ? null : value.toString(), users.currentUser().id());
        changed(task.getKey(), "custom");

        return detail(key);
    }

    public boolean toggleWatch(String key) {
        Task task = require(key);
        UUID userId = users.currentUser().id();
        Optional<TaskWatcher> existing = watchers.findByTaskIdAndUserId(task.getId(), userId);

        if (existing.isPresent()) {
            watchers.delete(existing.get());
            changed(task.getKey(), "watchers");
            return false;
        }

        watchers.save(new TaskWatcher(UUID.randomUUID(), task.getId(), userId));
        changed(task.getKey(), "watchers");
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledTask> scheduled() {
        Map<String, List<String>> blocks = jdbc.sql("""
                        SELECT f.task_key AS from_key, t.task_key AS to_key
                        FROM task_relation r
                        JOIN task f ON f.id = r.from_task
                        JOIN task t ON t.id = r.to_task
                        WHERE r.kind = 'blocks'
                        """)
                .query((rs, rowNum) -> Map.entry(rs.getString("from_key"), rs.getString("to_key")))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        String sprint = workspace.settings().currentSprint();
        List<Task> scope = sprint == null || sprint.isBlank()
                ? tasks.findAll(Sort.by("key"))
                : tasks.findBySprintCodeOrderByKeyAsc(sprint);

        return scope.stream()
                .filter(task -> task.getStartDate() != null && task.getEndDate() != null)
                .sorted(Comparator.comparing(Task::getStartDate))
                .map(task -> new ScheduledTask(
                        task.getKey(),
                        task.getTitle(),
                        task.getEpicId(),
                        task.getAssigneeId(),
                        task.getStartDate(),
                        task.getEndDate(),
                        task.getProgress(),
                        blocks.getOrDefault(task.getKey(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkloadRow> workloadByAssignee() {
        Map<UUID, Integer> byUser = new LinkedHashMap<>();

        for (Task task : tasks.findAll()) {
            if (task.getAssigneeId() == null || task.getEstimate() == null) {
                continue;
            }
            if (isDone(task)) {
                continue;
            }
            byUser.merge(task.getAssigneeId(), task.getEstimate(), Integer::sum);
        }

        return users.findActive().stream()
                .map(user -> new WorkloadRow(user.id(), byUser.getOrDefault(user.id(), 0), user.capacity()))
                .sorted(Comparator.comparingInt(WorkloadRow::points).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countInProgress() {
        return (int) tasks.findAll().stream()
                .filter(task -> categoryOf(task) == StatusCategory.IN_FLIGHT)
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public int countCompletedSince(Instant since) {
        return (int) tasks.findAll().stream()
                .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(since))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public Double averageCycleTimeDays() {
        List<Double> spans = tasks.findAll().stream()
                .filter(task -> task.getStartedAt() != null && task.getCompletedAt() != null)
                .map(task -> ChronoUnit.HOURS.between(task.getStartedAt(), task.getCompletedAt()) / 24.0)
                .toList();

        return spans.isEmpty() ? null : spans.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private void remove(List<Task> found) {
        UserView actor = users.currentUser();

        for (Task task : found) {
            jdbc.sql("DELETE FROM task_relation WHERE from_task = ? OR to_task = ?")
                    .params(task.getId(), task.getId())
                    .update();
            events.publishEvent(new TaskEvents.TaskDeleted(
                    task.getKey(), actor.id(), Instant.now(), ActorContext.currentLabel()));
            changed(task.getKey(), "deleted");
        }

        tasks.deleteAll(found);
    }

    private void markDone(Task task, Subtask subtask, boolean done) {
        if (subtask.isDone() == done) {
            return;
        }

        subtask.setDone(done);
        if (done) {
            record(task, "subtask", null, subtask.getTitle(), users.currentUser().id());
        }
    }

    private void recalculateProgress(Task task) {
        List<Subtask> all = subtasks.findByTaskIdOrderByPositionAsc(task.getId());

        if (!all.isEmpty()) {
            long done = all.stream().filter(Subtask::isDone).count();
            task.setProgress((int) Math.round(done * 100.0 / all.size()));
        }

        task.touch();
    }

    private void applyPriority(Task task, Priority next, UUID actorId) {
        if (next == task.getPriority()) {
            return;
        }

        record(task, "priority", "priority." + task.getPriority().code(), "priority." + next.code(), actorId);
        task.setPriority(next);
    }

    private void applyAssignee(Task task, UUID assigneeId, UUID actorId) {
        UUID next = knownUser(assigneeId);
        if (Objects.equals(task.getAssigneeId(), next)) {
            return;
        }

        String oldName = task.getAssigneeId() == null ? null : nameOf(task.getAssigneeId());
        task.setAssigneeId(next);
        record(task, "assignee", oldName, next == null ? null : nameOf(next), actorId);

        if (next != null) {
            events.publishEvent(new TaskEvents.TaskAssigned(
                    task.getKey(), next, actorId, Instant.now(), ActorContext.currentLabel()));
        }
    }

    private void applyStatus(Task task, UUID statusId, UUID actorId) {
        StatusView target = requireStatus(statusId);
        StatusView current = requireStatus(task.getStatusId());

        if (current.id().equals(target.id())) {
            return;
        }
        if (workspace.settings().blockDisallowedDrag() && !workspace.transitionAllowed(current.id(), target.id())) {
            throw new RuleViolationException(
                    "The transition from status " + current.label() + " to " + target.label() + " is not allowed");
        }

        task.setStatusId(target.id());
        record(task, "status", "status." + current.code(), "status." + target.code(), actorId);

        if (target.category() == StatusCategory.IN_FLIGHT && task.getStartedAt() == null) {
            task.setStartedAt(Instant.now());
        }
        if (target.category() == StatusCategory.DONE) {
            task.setCompletedAt(Instant.now());
            task.setProgress(100);
        } else {
            task.setCompletedAt(null);
        }

        events.publishEvent(new TaskEvents.TaskStatusChanged(
                task.getKey(), current.code(), target.code(), current.label(), target.label(), actorId,
                Instant.now(), ActorContext.currentLabel()));
    }

    private void changed(String taskKey, String change) {
        OrganizationContext scope = OrganizationContextHolder.currentOrNull();
        if (scope == null || !scope.hasOrganization()) {
            return;
        }

        events.publishEvent(new RealtimeEvents.TaskChanged(
                scope.organizationId(), taskKey, change, scope.userId(), Instant.now()));
    }

    private void record(Task task, String field, String oldValue, String newValue, UUID actorId) {
        history.save(new TaskHistoryEntry(
                UUID.randomUUID(), task.getId(), field, oldValue, newValue, actorId, ActorContext.currentLabel()));
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(UserView::shortName).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> sprints() {
        return jdbc.sql("SELECT DISTINCT sprint_code FROM task WHERE sprint_code IS NOT NULL ORDER BY sprint_code")
                .query(String.class)
                .list();
    }

    private String sprintOf(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String current = workspace.settings().currentSprint();
        return current == null || current.isBlank() ? null : current;
    }

    private Task require(String key) {
        return tasks.findByKey(key).orElseThrow(() -> NotFoundException.of("Task", key));
    }

    private Subtask requireSubtask(Task task, UUID subtaskId) {
        Subtask subtask = subtasks.findById(subtaskId)
                .orElseThrow(() -> NotFoundException.of("Podzadanie", subtaskId));

        if (!subtask.getTaskId().equals(task.getId())) {
            throw new RuleViolationException("The subtask does not belong to task " + task.getKey());
        }

        return subtask;
    }

    private StatusView requireStatus(UUID statusId) {
        return workspace.statusById(statusId).orElseThrow(() -> NotFoundException.of("Status", statusId));
    }

    private StatusView firstStatus() {
        return workspace.statuses().stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No statuses defined"));
    }

    private UUID knownUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(UserView::id).orElseThrow(() -> NotFoundException.of("Osoba", userId));
    }

    private UUID knownEpic(UUID epicId) {
        if (epicId == null) {
            return null;
        }
        return workspace.epicById(epicId).map(epic -> epic.id())
                .orElseThrow(() -> NotFoundException.of("Epik", epicId));
    }

    private void requireEnabled(Set<String> disabled, TaskField field, Object value) {
        if (isEmpty(value) || !disabled.contains(field.key())) {
            return;
        }
        throw new RuleViolationException("Field " + field.key() + " is disabled in this project");
    }

    private void requireEnabled(Set<String> disabled, PatchBody patch, String name, TaskField field) {
        if (patch.has(name)) {
            requireEnabled(disabled, field, patch.raw(name));
        }
    }

    private void requireCustomEnabled(Set<String> disabled, String fieldKey) {
        if (disabled.contains(TaskField.customKey(fieldKey))) {
            throw new RuleViolationException("Field " + fieldKey + " is disabled in this project");
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> items) {
            return items.isEmpty();
        }
        if (value instanceof Map<?, ?> entries) {
            return entries.isEmpty();
        }
        return false;
    }

    private Map<String, Object> knownCustomFields(Map<String, Object> custom) {
        for (String fieldKey : custom.keySet()) {
            workspace.customFieldByKey(fieldKey)
                    .orElseThrow(() -> NotFoundException.of("Custom field", fieldKey));
        }
        return custom;
    }

    private String relationKind(String kind) {
        if (!RELATION_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown link kind: " + kind);
        }
        return kind;
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Field " + field + " is not a date: " + value);
        }
    }

    private String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + ": a value is required");
        }
        return value.trim();
    }

    private boolean isDone(Task task) {
        return categoryOf(task) == StatusCategory.DONE;
    }

    private StatusCategory categoryOf(Task task) {
        return workspace.statusById(task.getStatusId()).map(StatusView::category).orElse(StatusCategory.NOT_STARTED);
    }

    private List<SubtaskView> subtaskViews(Task task) {
        return subtasks.findByTaskIdOrderByPositionAsc(task.getId()).stream()
                .map(TaskService::toView)
                .toList();
    }

    private List<RelationView> relationsOf(UUID taskId) {
        return jdbc.sql("""
                        SELECT r.kind, t.task_key
                        FROM task_relation r
                        JOIN task t ON t.id = r.to_task
                        WHERE r.from_task = ?
                        ORDER BY r.kind, t.task_key
                        """)
                .param(taskId)
                .query((rs, rowNum) -> new RelationView(rs.getString("kind"), rs.getString("task_key")))
                .list();
    }

    private Map<String, Object> visibleCustomFields(Task task) {
        OrganizationContext context = OrganizationContextHolder.current();

        List<String> restricted = workspace.customFields().stream()
                .filter(field -> field.requiredPermission() != null)
                .filter(field -> !context.can(field.requiredPermission()))
                .map(CustomFieldView::fieldKey)
                .toList();

        if (restricted.isEmpty()) {
            return task.getCustom();
        }

        Map<String, Object> visible = new LinkedHashMap<>(task.getCustom());
        restricted.forEach(visible::remove);
        return visible;
    }

    private TaskSummary summaryOf(Task task) {
        List<Subtask> all = subtasks.findByTaskIdOrderByPositionAsc(task.getId());
        return toSummary(task, all.size(), (int) all.stream().filter(Subtask::isDone).count(),
                (int) comments.countByTaskId(task.getId()));
    }

    private TaskSummary toSummary(Task task, int subtasksTotal, int subtasksDone, int commentCount) {
        String statusCode = workspace.statusById(task.getStatusId()).map(StatusView::code).orElse("backlog");
        return new TaskSummary(
                task.getId(),
                task.getKey(),
                task.getTitle(),
                task.getStatusId(),
                statusCode,
                task.getPriority(),
                task.getAssigneeId(),
                List.copyOf(task.getLabels()),
                task.getDueDate(),
                task.getStartDate(),
                task.getEndDate(),
                task.getEstimate(),
                task.getProgress(),
                subtasksDone,
                subtasksTotal,
                commentCount,
                task.isAutomated(),
                task.getEpicId(),
                task.getProjectId(),
                task.getSprintCode());
    }

    private static SubtaskView toView(Subtask subtask) {
        return new SubtaskView(subtask.getId(), subtask.getTitle(), subtask.isDone(), subtask.getAssigneeId());
    }

    public record NewTask(
            String title,
            String description,
            UUID statusId,
            UUID projectId,
            String sprintCode,
            String priority,
            UUID assigneeId,
            UUID reviewerId,
            String dueDate,
            Integer estimate,
            UUID epicId,
            List<String> labels,
            Map<String, Object> custom) {
    }
}
