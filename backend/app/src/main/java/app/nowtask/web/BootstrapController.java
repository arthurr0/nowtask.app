package app.nowtask.web;

import app.nowtask.tasks.api.Tasks;
import app.nowtask.workspace.api.WorkspaceViews.SettingsView;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.automation.api.Automations;
import app.nowtask.identity.OnboardingService;
import app.nowtask.identity.api.NavItemView;
import app.nowtask.identity.api.NavPreferences;
import app.nowtask.identity.api.OnboardingView;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.EpicView;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;
import app.nowtask.workspace.api.WorkspaceViews.SavedViewView;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;
import app.nowtask.workspace.api.WorkspaceViews.TaskFieldSettingView;
import app.nowtask.workspace.api.WorkspaceViews.TransitionView;

@RestController
@RequestMapping("/api/bootstrap")
class BootstrapController {
    private final UserDirectory users;
    private final NavPreferences navPreferences;
    private final Workspace workspace;
    private final Automations automations;
    private final Tasks tasks;
    private final OnboardingService onboarding;

    BootstrapController(
            UserDirectory users,
            NavPreferences navPreferences,
            Workspace workspace,
            Automations automations,
            Tasks tasks,
            OnboardingService onboarding) {
        this.users = users;
        this.navPreferences = navPreferences;
        this.workspace = workspace;
        this.automations = automations;
        this.tasks = tasks;
        this.onboarding = onboarding;
    }

    record Bootstrap(
            UserView currentUser,
            List<UserView> users,
            List<TeamView> teams,
            List<ProjectView> projects,
            List<StatusView> statuses,
            List<TransitionView> transitions,
            List<EpicView> epics,
            List<SavedViewView> savedViews,
            List<TaskFieldSettingView> taskFieldSettings,
            SettingsView settings,
            List<NavItemView> navigation,
            List<String> permissions,
            List<String> sprints,
            int activeRuleCount,
            OnboardingView onboarding) {
    }

    @GetMapping
    Bootstrap bootstrap() {
        return new Bootstrap(
                users.currentUser(),
                users.findAll(),
                users.findTeams(),
                workspace.projects(),
                workspace.statuses(),
                workspace.transitions(),
                workspace.epics(),
                workspace.savedViews(),
                workspace.taskFieldSettings(),
                workspace.settings(),
                navPreferences.currentNavigation(),
                app.nowtask.shared.OrganizationContextHolder.current().permissions().stream()
                        .map(app.nowtask.shared.Permission::code)
                        .sorted()
                        .toList(),
                tasks.sprints(),
                automations.activeRuleCount(),
                onboarding.current().orElse(null));
    }
}
