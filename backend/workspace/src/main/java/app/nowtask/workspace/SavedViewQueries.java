package app.nowtask.workspace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import app.nowtask.shared.TaskQuery;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;

@Component
class SavedViewQueries {

    private static final String SELECT = """
            SELECT v.id, v.name, v.code, v.query, v.shared, v.owner_id,
                   CASE v.code
                       WHEN 'view.atRisk' THEN (
                           SELECT count(*) FROM task t
                           JOIN status_def s ON s.id = t.status_id
                           WHERE t.due_date IS NOT NULL
                             AND t.due_date <= CURRENT_DATE + 2
                             AND s.category <> 'done')
                       WHEN 'view.unassigned' THEN (
                           SELECT count(*) FROM task t
                           JOIN status_def s ON s.id = t.status_id
                           WHERE t.assignee_id IS NULL AND s.category <> 'done')
                       WHEN 'view.automated' THEN (
                           SELECT count(*) FROM task WHERE automated)
                       ELSE -1
                   END AS builtin_count
            FROM saved_view v
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    SavedViewQueries(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    List<Row> visibleTo(UUID userId) {
        return jdbc.sql(SELECT + " WHERE v.code IS NOT NULL OR v.shared OR v.owner_id = ? ORDER BY v.position, v.name")
                .param(userId)
                .query(this::toRow)
                .list();
    }

    Optional<Row> byId(UUID id) {
        return jdbc.sql(SELECT + " WHERE v.id = ?")
                .param(id)
                .query(this::toRow)
                .optional();
    }

    String asJson(TaskQuery query) {
        return mapper.writeValueAsString(query == null ? TaskQuery.empty() : query);
    }

    private Row toRow(ResultSet rs, int rowNum) throws SQLException {
        SavedViewView view = new SavedViewView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                mapper.readValue(rs.getString("query"), TaskQuery.class),
                0,
                rs.getBoolean("shared"),
                rs.getObject("owner_id", UUID.class));
        return new Row(view, rs.getInt("builtin_count"));
    }

    record Row(SavedViewView view, int builtinCount) {

        SavedViewView withCount(int count) {
            return new SavedViewView(
                    view.id(), view.name(), view.code(), view.query(), count, view.shared(), view.ownerId());
        }

        boolean builtin() {
            return view.code() != null;
        }
    }
}
