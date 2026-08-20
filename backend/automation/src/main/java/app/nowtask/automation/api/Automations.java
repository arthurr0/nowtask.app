package app.nowtask.automation.api;

import java.util.List;
import app.nowtask.automation.api.AutomationViews.RuleUsage;
import app.nowtask.automation.api.AutomationViews.RuleView;
import app.nowtask.automation.api.AutomationViews.RunView;

public interface Automations {
    List<RuleView> rules();

    List<RuleUsage> usage();

    List<RunView> recentRuns();

    List<RunView> runsOf(java.util.UUID ruleId);

    int activeRuleCount();

    List<RuleView> rulesTouching(String taskKey);
}
