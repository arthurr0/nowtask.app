package app.nowtask.automation.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AutomationViews {
    private AutomationViews() {
    }

    public record RuleView(
            UUID id,
            String name,
            String summary,
            String scopeLabel,
            boolean enabled,
            boolean draft,
            int runs30d,
            Map<String, Object> trigger,
            Map<String, Object> conditions,
            List<Map<String, Object>> actions,
            UUID editedById,
            LocalDate editedAt) {
    }

    public record RunView(
            UUID id,
            UUID ruleId,
            String ruleName,
            String taskKey,
            String outcome,
            String detailKey,
            Map<String, Object> detailParams,
            Instant createdAt) {
    }

    public record RuleUsage(UUID ruleId, String ruleName, int runs, boolean failing) {
    }
}
