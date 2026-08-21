package app.nowtask.workspace.preset;

import java.util.List;
import java.util.Map;
import app.nowtask.shared.StatusCategory;

public record PresetDefinition(
        String code,
        List<Status> statuses,
        List<Transition> transitions,
        List<Field> fields,
        List<View> views,
        List<Rule> rules,
        List<Milestone> milestones,
        boolean sprintsEnabled,
        boolean milestonesEnabled,
        String estimateUnit,
        boolean wipEnforced,
        boolean dependencyGuard,
        Boolean blockDisallowedDrag,
        String defaultViewCode) {

    public record Status(String code, String label, StatusCategory category, Integer wipLimit, String swatch) {
    }

    public record Transition(String from, String to, String requirement) {
    }

    public record Field(String name, String fieldKey, String type, String restrictedToRole, List<Option> options) {
    }

    public record Option(String value, String label, String swatch) {
    }

    public record View(String code, Map<String, Object> query, String statusCode, boolean supported) {
    }

    public record Rule(
            String name,
            String summary,
            Map<String, Object> trigger,
            Map<String, Object> conditions,
            List<Map<String, Object>> actions,
            boolean supported) {
    }

    public record Milestone(String name, int dueInDays) {
    }

    public int statusCount() {
        return statuses.size();
    }
}
