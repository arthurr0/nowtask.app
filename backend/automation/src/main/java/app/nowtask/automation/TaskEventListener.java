package app.nowtask.automation;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import app.nowtask.shared.events.TaskEvents;

@Component
class TaskEventListener {

    private final AutomationService automations;
    private final RuleEngine engine;

    TaskEventListener(AutomationService automations, RuleEngine engine) {
        this.automations = automations;
        this.engine = engine;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onStatusChanged(TaskEvents.TaskStatusChanged event) {
        if (event.ruleName() != null) {
            return;
        }

        for (AutomationRule rule : automations.rulesTriggeredBy("statusChanged")) {
            if (!rule.isEnabled() || rule.isDraft()) {
                continue;
            }

            String expected = rule.triggerValue();
            boolean matchesTrigger = expected == null
                    || expected.isBlank()
                    || expected.equals("status." + event.toStatusCode())
                    || expected.equals(event.toStatusCode());

            if (!matchesTrigger) {
                automations.recordRun(rule, event.taskKey(), "skipped", "run.triggerNotMatched",
                        Map.of("value", event.toStatusCode()));
                continue;
            }

            fire(rule, event.taskKey());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onAssigned(TaskEvents.TaskAssigned event) {
        if (event.ruleName() != null) {
            return;
        }

        for (AutomationRule rule : automations.rulesTriggeredBy("assigned")) {
            if (!rule.isEnabled() || rule.isDraft()) {
                continue;
            }
            fire(rule, event.taskKey());
        }
    }

    private void fire(AutomationRule rule, String taskKey) {
        RuleEngine.TaskOutcome outcome = engine.runOn(rule, taskKey);

        if (!outcome.matched()) {
            automations.recordRun(rule, taskKey, "skipped", "run.conditionNotMet", Map.of());
            return;
        }

        long applied = outcome.actions().stream().filter(RuleEngine.ActionOutcome::applied).count();
        boolean failed = outcome.actions().stream()
                .anyMatch(action -> "run.actionFailed".equals(action.detailKey()));

        if (failed) {
            automations.recordRun(rule, taskKey, "error", "run.actionFailed",
                    Map.of("count", String.valueOf(outcome.actions().size())));
            return;
        }

        automations.recordRun(rule, taskKey, applied > 0 ? "ok" : "skipped",
                applied > 0 ? "run.actionsExecuted" : "run.actionsSkipped",
                Map.of("count", String.valueOf(applied > 0 ? applied : outcome.actions().size())));
    }
}
