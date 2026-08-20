package app.nowtask.tasks;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import app.nowtask.shared.Priority;
import app.nowtask.shared.TaskQuery;

record TaskFilter(String where, List<Object> params) {

    private static final Map<String, String> SORTABLE = Map.of(
            "key", "t.task_key",
            "title", "t.title",
            "status", "s.position",
            "priority", "CASE t.priority WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END",
            "dueDate", "t.due_date",
            "estimate", "t.estimate",
            "created", "t.created_at",
            "updated", "t.updated_at");

    static TaskFilter of(TaskQuery source) {
        TaskQuery query = source == null ? TaskQuery.empty() : source;
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (hasText(query.query())) {
            clauses.add("(t.title ILIKE ? OR t.task_key ILIKE ?)");
            String pattern = "%" + query.query().trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (query.statusId() != null) {
            clauses.add("t.status_id = ?");
            params.add(query.statusId());
        }
        if (query.assigneeId() != null) {
            clauses.add("t.assignee_id = ?");
            params.add(query.assigneeId());
        }
        if (hasText(query.label())) {
            clauses.add("EXISTS (SELECT 1 FROM task_label tl WHERE tl.task_id = t.id AND tl.label = ?)");
            params.add(query.label().trim());
        }
        if (hasText(query.priority())) {
            clauses.add("t.priority = ?");
            params.add(Priority.of(query.priority()).code());
        }
        if (query.epicId() != null) {
            clauses.add("t.epic_id = ?");
            params.add(query.epicId());
        }
        if (query.projectId() != null) {
            clauses.add("t.project_id = ?");
            params.add(query.projectId());
        }
        if (hasText(query.dueBefore())) {
            clauses.add("t.due_date IS NOT NULL AND t.due_date < ?");
            params.add(parseDate(query.dueBefore()));
        }
        if (query.unassigned() != null) {
            clauses.add(query.unassigned() ? "t.assignee_id IS NULL" : "t.assignee_id IS NOT NULL");
        }
        if (query.automated() != null) {
            clauses.add(query.automated() ? "t.automated" : "NOT t.automated");
        }
        if (hasText(query.sprint())) {
            clauses.add("t.sprint_code = ?");
            params.add(query.sprint().trim());
        }

        return new TaskFilter(
                clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses),
                List.copyOf(params));
    }

    static String orderBy(String sort) {
        if (!hasText(sort)) {
            return " ORDER BY t.task_key";
        }

        boolean descending = sort.startsWith("-");
        String field = descending ? sort.substring(1) : sort;
        String column = SORTABLE.get(field);

        if (column == null) {
            throw new IllegalArgumentException("Nieznane sortowanie: " + sort);
        }

        return " ORDER BY " + column + (descending ? " DESC" : " ASC") + " NULLS LAST, t.task_key";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }
}
