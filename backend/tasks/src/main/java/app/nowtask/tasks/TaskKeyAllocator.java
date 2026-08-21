package app.nowtask.tasks;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;

@Component
class TaskKeyAllocator {

    private final JdbcClient jdbc;

    TaskKeyAllocator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    String nextKey(ProjectView project) {
        seed(project.id());
        int number = jdbc.sql("""
                        UPDATE task_key_sequence
                        SET next_value = next_value + 1
                        WHERE project_id = ?
                        RETURNING next_value - 1 AS allocated
                        """)
                .param(project.id())
                .query(Integer.class)
                .single();

        return project.code() + "-" + number;
    }

    private void seed(UUID projectId) {
        jdbc.sql("""
                        INSERT INTO task_key_sequence (project_id, next_value)
                        VALUES (?, 1)
                        ON CONFLICT (organization_id, project_id) DO NOTHING
                        """)
                .param(projectId)
                .update();
    }
}
