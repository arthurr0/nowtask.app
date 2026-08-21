package app.nowtask.workspace.api;

import java.util.List;
import app.nowtask.shared.StatusCategory;

public final class PresetViews {

    private PresetViews() {
    }

    public record PresetSummaryView(
            String code,
            int statusCount,
            boolean sprintsEnabled,
            boolean milestonesEnabled,
            String estimateUnit,
            boolean wipEnforced,
            boolean dependencyGuard,
            String defaultViewCode) {
    }

    public record PresetStatusView(
            String code,
            String label,
            StatusCategory category,
            Integer wipLimit,
            int position,
            String swatch) {
    }

    public record PresetTransitionView(String from, String to, String requirement) {
    }

    public record PresetFieldView(String name, String fieldKey, String type, List<String> options) {
    }

    public record PresetRuleView(String name, String summary, boolean supported) {
    }

    public record PresetDetailView(
            PresetSummaryView summary,
            List<PresetStatusView> statuses,
            List<PresetTransitionView> transitions,
            List<PresetFieldView> fields,
            List<String> views,
            List<PresetRuleView> rules) {
    }
}
