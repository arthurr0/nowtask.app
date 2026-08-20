package app.nowtask.tasks;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.Priority;
import app.nowtask.shared.TaskCounts;
import app.nowtask.shared.TaskQuery;
import app.nowtask.tasks.api.TaskViews.TaskGroup;
import app.nowtask.tasks.api.TaskViews.TaskPage;
import app.nowtask.tasks.api.TaskViews.TaskSummary;

@Service
@Transactional(readOnly = true)
public class TaskQueryService implements TaskCounts {

    private static final String SELECT_SUMMARY = """
            SELECT t.id, t.task_key, t.title, t.status_id, s.code AS status_code, t.priority, t.assignee_id,
                   t.due_date, t.start_date, t.end_date, t.estimate, t.progress, t.automated, t.epic_id,
                   t.project_id, t.sprint_code,
                   labels.names AS labels,
                   COALESCE(sub.total, 0) AS subtasks_total,
                   COALESCE(sub.done, 0) AS subtasks_done,
                   COALESCE(discussion.total, 0) AS comment_count
            FROM task t
            JOIN status_def s ON s.id = t.status_id
            LEFT JOIN LATERAL (
                SELECT array_agg(l.label ORDER BY l.label) AS names
                FROM task_label l WHERE l.task_id = t.id
            ) labels ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) AS total, count(*) FILTER (WHERE b.done) AS done
                FROM subtask b WHERE b.task_id = t.id
            ) sub ON TRUE
            LEFT JOIN LATERAL (
                SELECT count(*) AS total
                FROM task_comment c WHERE c.task_id = t.id
            ) discussion ON TRUE
            """;

    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "low", "Low",
            "medium", "Medium",
            "high", "High",
            "critical", "Critical");

    private final JdbcClient jdbc;
    private final UserDirectory users;

    TaskQueryService(JdbcClient jdbc, UserDirectory users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public TaskPage page(TaskQuery source) {
        TaskQuery query = source == null ? TaskQuery.empty() : source;
        TaskFilter filter = TaskFilter.of(query);
        int total = count(filter);

        List<Object> params = new ArrayList<>(filter.params());
        String limit = "";
        if (query.paged()) {
            limit = " LIMIT ? OFFSET ?";
            params.add(query.pageSize());
            params.add(query.pageNumber() * query.pageSize());
        }

        List<TaskSummary> items = jdbc
                .sql(SELECT_SUMMARY + filter.where() + TaskFilter.orderBy(query.sort()) + limit)
                .params(params)
                .query(TaskQueryService::toSummary)
                .list();

        return new TaskPage(
                items,
                total,
                query.pageNumber(),
                query.paged() ? query.pageSize() : items.size(),
                groups(query, filter));
    }

    public List<TaskSummary> search(String text, int limit) {
        return page(TaskQuery.ofText(text, limit)).items();
    }

    @Override
    public int count(TaskQuery query) {
        return count(TaskFilter.of(query));
    }

    private int count(TaskFilter filter) {
        return jdbc.sql("SELECT count(*) FROM task t" + filter.where())
                .params(filter.params())
                .query(Integer.class)
                .single();
    }

    private List<TaskGroup> groups(TaskQuery query, TaskFilter filter) {
        if (query.groupBy() == null || query.groupBy().isBlank()) {
            return null;
        }

        return switch (query.groupBy()) {
            case "status" -> byStatus(filter);
            case "assignee" -> byAssignee(filter);
            case "priority" -> byPriority(filter);
            case "epic" -> byEpic(filter);
            case "label" -> byLabel(filter);
            default -> throw new IllegalArgumentException("Nieznane grupowanie: " + query.groupBy());
        };
    }

    private List<TaskGroup> byStatus(TaskFilter filter) {
        return jdbc.sql("""
                        SELECT s.id::TEXT AS group_key, s.label AS group_label, count(*) AS total
                        FROM task t
                        JOIN status_def s ON s.id = t.status_id
                        """ + filter.where() + " GROUP BY s.id, s.label, s.position ORDER BY s.position")
                .params(filter.params())
                .query(TaskQueryService::toGroup)
                .list();
    }

    private List<TaskGroup> byAssignee(TaskFilter filter) {
        Map<UUID, UserView> people = users.findAll().stream()
                .collect(Collectors.toMap(UserView::id, person -> person));

        return jdbc.sql("""
                        SELECT COALESCE(t.assignee_id::TEXT, '') AS group_key, '' AS group_label, count(*) AS total
                        FROM task t
                        """ + filter.where() + " GROUP BY t.assignee_id ORDER BY count(*) DESC")
                .params(filter.params())
                .query(TaskQueryService::toGroup)
                .list().stream()
                .map(group -> new TaskGroup(group.key(), personLabel(group.key(), people), group.count()))
                .toList();
    }

    private List<TaskGroup> byPriority(TaskFilter filter) {
        return jdbc.sql("""
                        SELECT t.priority AS group_key, t.priority AS group_label, count(*) AS total
                        FROM task t
                        """ + filter.where() + """
                         GROUP BY t.priority
                         ORDER BY CASE t.priority
                             WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END
                        """)
                .params(filter.params())
                .query(TaskQueryService::toGroup)
                .list().stream()
                .map(group -> new TaskGroup(group.key(), PRIORITY_LABELS.getOrDefault(group.key(), group.key()),
                        group.count()))
                .toList();
    }

    private List<TaskGroup> byEpic(TaskFilter filter) {
        return jdbc.sql("""
                        SELECT COALESCE(e.id::TEXT, '') AS group_key,
                               COALESCE(e.name, 'Bez epiku') AS group_label,
                               count(*) AS total
                        FROM task t
                        LEFT JOIN epic e ON e.id = t.epic_id
                        """ + filter.where() + " GROUP BY e.id, e.name ORDER BY e.name NULLS LAST")
                .params(filter.params())
                .query(TaskQueryService::toGroup)
                .list();
    }

    private List<TaskGroup> byLabel(TaskFilter filter) {
        return jdbc.sql("""
                        SELECT l.label AS group_key, l.label AS group_label, count(*) AS total
                        FROM task t
                        JOIN task_label l ON l.task_id = t.id
                        """ + filter.where() + " GROUP BY l.label ORDER BY count(*) DESC, l.label")
                .params(filter.params())
                .query(TaskQueryService::toGroup)
                .list();
    }

    private String personLabel(String key, Map<UUID, UserView> people) {
        if (key == null || key.isEmpty()) {
            return "Nieprzypisane";
        }
        UserView person = people.get(UUID.fromString(key));
        return person == null ? "Nieznany" : person.shortName();
    }

    private static TaskGroup toGroup(ResultSet rs, int rowNum) throws SQLException {
        return new TaskGroup(rs.getString("group_key"), rs.getString("group_label"), rs.getInt("total"));
    }

    private static TaskSummary toSummary(ResultSet rs, int rowNum) throws SQLException {
        return new TaskSummary(
                rs.getObject("id", UUID.class),
                rs.getString("task_key"),
                rs.getString("title"),
                rs.getObject("status_id", UUID.class),
                rs.getString("status_code"),
                Priority.of(rs.getString("priority")),
                rs.getObject("assignee_id", UUID.class),
                labels(rs),
                rs.getObject("due_date", LocalDate.class),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getObject("estimate", Integer.class),
                rs.getInt("progress"),
                rs.getInt("subtasks_done"),
                rs.getInt("subtasks_total"),
                rs.getInt("comment_count"),
                rs.getBoolean("automated"),
                rs.getObject("epic_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("sprint_code"));
    }

    private static List<String> labels(ResultSet rs) throws SQLException {
        Array array = rs.getArray("labels");
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }
}
