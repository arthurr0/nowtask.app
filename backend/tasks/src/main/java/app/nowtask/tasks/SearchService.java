package app.nowtask.tasks;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.automation.api.AutomationViews.RuleView;
import app.nowtask.automation.api.Automations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.tasks.api.TaskViews.TaskSummary;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final int LIMIT = 5;

    private final TaskQueryService queries;
    private final Automations automations;
    private final UserDirectory users;
    private final Workspace workspace;

    SearchService(TaskQueryService queries, Automations automations, UserDirectory users, Workspace workspace) {
        this.queries = queries;
        this.automations = automations;
        this.users = users;
        this.workspace = workspace;
    }

    public SearchResult search(String text) {
        if (text == null || text.isBlank()) {
            return new SearchResult(List.of(), List.of(), List.of(), List.of());
        }

        String needle = text.trim().toLowerCase();

        return new SearchResult(
                queries.search(text.trim(), LIMIT),
                automations.rules().stream()
                        .filter(rule -> matches(rule.name(), needle) || matches(rule.summary(), needle))
                        .limit(LIMIT)
                        .toList(),
                users.findAll().stream()
                        .filter(user -> matches(user.name(), needle) || matches(user.email(), needle))
                        .limit(LIMIT)
                        .toList(),
                workspace.savedViews().stream()
                        .filter(view -> matches(view.name(), needle) || matches(view.code(), needle))
                        .limit(LIMIT)
                        .toList());
    }

    private boolean matches(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    public record SearchResult(
            List<TaskSummary> tasks,
            List<RuleView> rules,
            List<UserView> people,
            List<SavedViewView> views) {
    }
}
