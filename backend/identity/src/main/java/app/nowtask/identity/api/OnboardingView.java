package app.nowtask.identity.api;

import java.time.Instant;
import java.util.List;

public record OnboardingView(
        String flow,
        String step,
        List<ChecklistItemView> checklist,
        boolean tourSeen,
        boolean completed,
        boolean dismissed) {

    public record ChecklistItemView(String code, boolean done, Instant at) {
    }
}
