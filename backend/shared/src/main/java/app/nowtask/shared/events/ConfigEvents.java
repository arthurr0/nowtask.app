package app.nowtask.shared.events;

import java.util.UUID;

public final class ConfigEvents {

    private ConfigEvents() {
    }

    public record RuleToggled(UUID organizationId, UUID ruleId, boolean enabled, UUID actorId) {
    }

    public record StatusFlowChanged(UUID organizationId, UUID projectId, UUID actorId) {
    }
}
