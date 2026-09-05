package app.nowtask.workspace;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.TaskCounts;
import app.nowtask.shared.TaskQuery;
import app.nowtask.workspace.SavedViewQueries.Row;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;

@Service
@Transactional
public class SavedViewService {

    private final JdbcClient jdbc;
    private final SavedViewQueries views;
    private final TaskCounts counts;
    private final UserDirectory users;

    SavedViewService(JdbcClient jdbc, SavedViewQueries views, TaskCounts counts, UserDirectory users) {
        this.jdbc = jdbc;
        this.views = views;
        this.counts = counts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SavedViewView> list() {
        return views.visibleTo(users.currentUser().id()).stream().map(this::withCount).toList();
    }

    public SavedViewView create(String name, TaskQuery query, Boolean shared) {
        String trimmed = requireName(name);
        TaskQuery checked = checked(query);
        UUID id = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), 0) + 1 FROM saved_view")
                .query(Integer.class)
                .single();

        jdbc.sql("""
                        INSERT INTO saved_view (id, code, position, name, query, shared, owner_id)
                        VALUES (?, NULL, ?, ?, ?::JSONB, ?, ?)
                        """)
                .params(id, position, trimmed, views.asJson(checked), Boolean.TRUE.equals(shared),
                        users.currentUser().id())
                .update();

        return read(id);
    }

    public SavedViewView update(UUID id, String name, TaskQuery query, Boolean shared) {
        Row row = require(id);
        if (row.builtin() && query != null) {
            throw new RuleViolationException("The query of a built-in view cannot be changed");
        }
        TaskQuery checked = query == null ? null : checked(query);

        if (name != null) {
            jdbc.sql("UPDATE saved_view SET name = ? WHERE id = ?").params(requireName(name), id).update();
        }
        if (checked != null) {
            jdbc.sql("UPDATE saved_view SET query = ?::JSONB WHERE id = ?").params(views.asJson(checked), id).update();
        }
        if (shared != null && !row.builtin()) {
            jdbc.sql("UPDATE saved_view SET shared = ? WHERE id = ?").params(shared, id).update();
        }

        return read(id);
    }

    public void reorder(List<UUID> ids) {
        int position = 1;
        for (UUID id : ids) {
            jdbc.sql("UPDATE saved_view SET position = ? WHERE id = ?").params(position++, id).update();
        }
    }

    public void delete(UUID id) {
        Row row = require(id);
        if (row.builtin()) {
            throw new RuleViolationException("The built-in view " + row.view().name() + " cannot be deleted");
        }
        jdbc.sql("DELETE FROM saved_view WHERE id = ?").param(id).update();
    }

    private TaskQuery checked(TaskQuery query) {
        TaskQuery normalized = (query == null ? TaskQuery.empty() : query).withCheckedColumns().normalized();
        counts.count(normalized);
        return normalized;
    }

    private SavedViewView read(UUID id) {
        return withCount(require(id));
    }

    private Row require(UUID id) {
        return views.byId(id).orElseThrow(() -> NotFoundException.of("Widok", id));
    }

    private SavedViewView withCount(Row row) {
        return row.withCount(counts.count(row.view().query()));
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The view name is required");
        }
        return name.trim();
    }
}
