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
            SELECT v.id, v.name, v.code, v.query, v.shared, v.owner_id, v.origin, v.project_id, v.position
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
        String origin = rs.getString("origin");
        SavedViewView view = new SavedViewView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                mapper.readValue(rs.getString("query"), TaskQuery.class).normalized(),
                0,
                rs.getBoolean("shared"),
                rs.getObject("owner_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                "builtin".equals(origin),
                rs.getInt("position"));
        return new Row(view, origin);
    }

    record Row(SavedViewView view, String origin) {

        SavedViewView withCount(int count) {
            return new SavedViewView(
                    view.id(), view.name(), view.code(), view.query(), count, view.shared(), view.ownerId(),
                    view.projectId(), view.builtin(), view.position());
        }

        boolean builtin() {
            return "builtin".equals(origin);
        }
    }
}
