package app.nowtask.identity;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.ViewPreferences;
import app.nowtask.shared.TaskOpenMode;
import app.nowtask.shared.TaskView;

@Service
@Transactional
class ViewPreferenceService implements ViewPreferences {

    private static final TaskView FALLBACK = TaskView.BOARD;
    private static final TaskOpenMode OPEN_MODE_FALLBACK = TaskOpenMode.DIALOG;

    private final JdbcClient jdbc;
    private final UserDirectory directory;

    ViewPreferenceService(JdbcClient jdbc, UserDirectory directory) {
        this.jdbc = jdbc;
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskView currentDefaultView() {
        return jdbc.sql("SELECT default_view_code FROM user_view_preference WHERE user_id = ?")
                .param(directory.currentUser().id())
                .query(String.class)
                .optional()
                .flatMap(TaskView::byCode)
                .orElse(FALLBACK);
    }

    @Override
    public TaskView replaceDefaultView(TaskView view) {
        if (view == null) {
            throw new IllegalArgumentException("A default view is required");
        }

        UUID userId = directory.currentUser().id();
        int updated = jdbc.sql("UPDATE user_view_preference SET default_view_code = ? WHERE user_id = ?")
                .params(view.code(), userId)
                .update();

        if (updated == 0) {
            jdbc.sql("INSERT INTO user_view_preference (user_id, default_view_code, task_open_mode) VALUES (?, ?, ?)")
                    .params(userId, view.code(), OPEN_MODE_FALLBACK.code())
                    .update();
        }

        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskOpenMode currentTaskOpenMode() {
        return jdbc.sql("SELECT task_open_mode FROM user_view_preference WHERE user_id = ?")
                .param(directory.currentUser().id())
                .query(String.class)
                .optional()
                .flatMap(TaskOpenMode::byCode)
                .orElse(OPEN_MODE_FALLBACK);
    }

    @Override
    public TaskOpenMode replaceTaskOpenMode(TaskOpenMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("A task open mode is required");
        }

        UUID userId = directory.currentUser().id();
        int updated = jdbc.sql("UPDATE user_view_preference SET task_open_mode = ? WHERE user_id = ?")
                .params(mode.code(), userId)
                .update();

        if (updated == 0) {
            jdbc.sql("INSERT INTO user_view_preference (user_id, default_view_code, task_open_mode) VALUES (?, ?, ?)")
                    .params(userId, FALLBACK.code(), mode.code())
                    .update();
        }

        return mode;
    }
}
