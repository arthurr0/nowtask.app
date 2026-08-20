package app.nowtask.automation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.automation.api.AutomationViews.RuleUsage;
import app.nowtask.automation.api.AutomationViews.RuleView;
import app.nowtask.automation.api.AutomationViews.RunView;
import app.nowtask.automation.api.Automations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class AutomationService implements Automations {
    private final AutomationRuleRepository rules;
    private final AutomationRunRepository runs;
    private final RuleEngine engine;
    private final UserDirectory users;

    AutomationService(AutomationRuleRepository rules, AutomationRunRepository runs, RuleEngine engine,
            UserDirectory users) {
        this.rules = rules;
        this.runs = runs;
        this.engine = engine;
        this.users = users;
    }

    public record RunReport(int matched, int applied, int skipped, List<String> taskKeys) {
    }

    public RuleView create(String name, String summary, String scopeLabel, Map<String, Object> trigger,
            Map<String, Object> conditions, List<Map<String, Object>> actions, boolean draft) {
        String cleanName = requireText(name, "Rule name");
        int position = rules.findAllByOrderByPositionAsc().stream()
                .mapToInt(AutomationRule::getPosition)
                .max()
                .orElse(0) + 1;

        AutomationRule rule = new AutomationRule(
                UUID.randomUUID(),
                cleanName,
                summary,
                scopeLabel,
                !draft,
                draft,
                trigger == null ? Map.of("kind", "manual", "value", "") : trigger,
                conditions == null ? emptyGroup() : conditions,
                actions == null ? List.of() : actions,
                currentUserId(),
                position);

        return toView(rules.save(rule));
    }

    public RuleView update(UUID id, Map<String, Object> body) {
        AutomationRule rule = rules.findById(id).orElseThrow(() -> NotFoundException.of("Rule", id));

        if (body.containsKey("name")) {
            rule.setName(requireText(String.valueOf(body.get("name")), "Rule name"));
        }
        if (body.containsKey("summary")) {
            rule.setSummary(body.get("summary") == null ? "" : String.valueOf(body.get("summary")));
        }
        if (body.containsKey("scopeLabel")) {
            rule.setScopeLabel(body.get("scopeLabel") == null ? "" : String.valueOf(body.get("scopeLabel")));
        }
        if (body.containsKey("enabled")) {
            rule.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        }
        if (body.containsKey("draft")) {
            rule.setDraft(Boolean.parseBoolean(String.valueOf(body.get("draft"))));
        }
        if (body.get("trigger") instanceof Map<?, ?> trigger) {
            rule.setTrigger(castMap(trigger));
        }
        if (body.get("conditions") instanceof Map<?, ?> conditions) {
            rule.setConditions(castMap(conditions));
        }
        if (body.get("actions") instanceof List<?> actions) {
            rule.setActions(castActions(actions));
        }

        rule.touch(currentUserId());
        return toView(rule);
    }

    public void delete(UUID id) {
        AutomationRule rule = rules.findById(id).orElseThrow(() -> NotFoundException.of("Rule", id));
        runs.deleteAll(runs.findTop20ByRuleIdOrderByCreatedAtDesc(id));
        rules.delete(rule);
    }

    public RunReport runNow(UUID id, String taskKey) {
        AutomationRule rule = rules.findById(id).orElseThrow(() -> NotFoundException.of("Rule", id));
        if (rule.getActions().isEmpty()) {
            throw new RuleViolationException("The rule has no action to perform");
        }

        List<RuleEngine.TaskOutcome> outcomes = taskKey == null || taskKey.isBlank()
                ? engine.runOnAll(rule)
                : List.of(engine.runOn(rule, taskKey.trim()));

        int applied = 0;
        int skipped = 0;
        List<String> touched = new java.util.ArrayList<>();

        for (RuleEngine.TaskOutcome outcome : outcomes) {
            if (!outcome.matched()) {
                continue;
            }
            touched.add(outcome.taskKey());
            long ok = outcome.actions().stream().filter(RuleEngine.ActionOutcome::applied).count();
            applied += (int) ok;
            skipped += outcome.actions().size() - (int) ok;
            recordRun(rule, outcome.taskKey(), ok > 0 ? "ok" : "skipped",
                    ok > 0 ? "run.actionsExecuted" : "run.actionsSkipped",
                    Map.of("count", String.valueOf(ok > 0 ? ok : outcome.actions().size())));
        }

        return new RunReport(touched.size(), applied, skipped, touched);
    }

    private static Map<String, Object> emptyGroup() {
        return Map.of("id", "g1", "kind", "group", "join", "and", "children", List.of());
    }

    private UUID currentUserId() {
        try {
            return users.currentUser().id();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new RuleViolationException(label + " is required");
        }
        return value.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castActions(List<?> list) {
        return (List<Map<String, Object>>) list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleView> rules() {
        return rules.findAllByOrderByPositionAsc().stream().map(AutomationService::toView).toList();
    }

    @Transactional(readOnly = true)
    public RuleView rule(UUID id) {
        return rules.findById(id).map(AutomationService::toView)
                .orElseThrow(() -> NotFoundException.of("Rule", id));
    }

    public RuleView toggle(UUID id) {
        AutomationRule rule = rules.findById(id).orElseThrow(() -> NotFoundException.of("Rule", id));
        rule.setEnabled(!rule.isEnabled());
        return toView(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public int activeRuleCount() {
        return (int) rules.findAll().stream().filter(rule -> rule.isEnabled() && !rule.isDraft()).count();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunView> runsOf(UUID ruleId) {
        Map<UUID, String> names = ruleNames();
        return runs.findTop20ByRuleIdOrderByCreatedAtDesc(ruleId).stream()
                .map(run -> toView(run, names))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunView> recentRuns() {
        Map<UUID, String> names = ruleNames();
        return runs.findTop20ByOrderByCreatedAtDesc().stream().map(run -> toView(run, names)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleView> rulesTouching(String taskKey) {
        List<UUID> ruleIds = runs.findTop50ByTaskKeyOrderByCreatedAtDesc(taskKey).stream()
                .map(AutomationRun::getRuleId)
                .distinct()
                .toList();

        return rules.findAllById(ruleIds).stream().map(AutomationService::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleUsage> usage() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        Map<UUID, Boolean> failing = runs.findTop20ByOrderByCreatedAtDesc().stream()
                .filter(run -> run.getCreatedAt().isAfter(cutoff))
                .collect(Collectors.toMap(
                        AutomationRun::getRuleId,
                        run -> "error".equals(run.getOutcome()),
                        (a, b) -> a || b));

        return rules.findAllByOrderByPositionAsc().stream()
                .filter(rule -> rule.getRuns30d() > 0)
                .sorted((a, b) -> Integer.compare(b.getRuns30d(), a.getRuns30d()))
                .map(rule -> new RuleUsage(
                        rule.getId(), rule.getName(), rule.getRuns30d(), failing.getOrDefault(rule.getId(), false)))
                .toList();
    }

    void recordRun(AutomationRule rule, String taskKey, String outcome, String detailKey, Map<String, Object> params) {
        runs.save(new AutomationRun(UUID.randomUUID(), rule.getId(), taskKey, outcome, detailKey, params));
        rule.bumpRuns();
    }

    List<AutomationRule> rulesTriggeredBy(String kind) {
        return rules.findAllByOrderByPositionAsc().stream()
                .filter(rule -> rule.triggeredBy(kind))
                .toList();
    }

    private Map<UUID, String> ruleNames() {
        return rules.findAll().stream()
                .collect(Collectors.toMap(AutomationRule::getId, AutomationRule::getName, (a, b) -> a, LinkedHashMap::new));
    }

    private static RuleView toView(AutomationRule rule) {
        return new RuleView(
                rule.getId(),
                rule.getName(),
                rule.getSummary(),
                rule.getScopeLabel(),
                rule.isEnabled(),
                rule.isDraft(),
                rule.getRuns30d(),
                rule.getTrigger(),
                rule.getConditions(),
                rule.getActions(),
                rule.getEditedBy(),
                rule.getEditedAt());
    }

    private static RunView toView(AutomationRun run, Map<UUID, String> names) {
        return new RunView(
                run.getId(),
                run.getRuleId(),
                names.getOrDefault(run.getRuleId(), ""),
                run.getTaskKey(),
                run.getOutcome(),
                run.getDetailKey(),
                run.getDetailParams(),
                run.getCreatedAt());
    }

    static Function<AutomationRule, UUID> idOf() {
        return AutomationRule::getId;
    }
}
