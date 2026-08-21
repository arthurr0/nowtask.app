package app.nowtask.identity;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.events.ConfigEvents;
import app.nowtask.shared.events.IdentityEvents;
import app.nowtask.shared.events.TaskEvents;

@Component
class OnboardingChecklistListener {

    private final OnboardingService onboarding;

    OnboardingChecklistListener(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onTaskCreated(TaskEvents.TaskCreated event) {
        tick(event.actorId(), OnboardingService.ITEM_CREATE_TASK, event.ruleName());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onStatusChanged(TaskEvents.TaskStatusChanged event) {
        tick(event.actorId(), OnboardingService.ITEM_MOVE_TASK, event.ruleName());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onInviteIssued(IdentityEvents.InviteIssued event) {
        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        if (context == null) {
            return;
        }
        onboarding.tick(event.organizationId(), context.userId(), OnboardingService.ITEM_INVITE_MEMBER);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onRuleToggled(ConfigEvents.RuleToggled event) {
        if (!event.enabled()) {
            return;
        }
        onboarding.tick(event.organizationId(), event.actorId(), OnboardingService.ITEM_TRY_AUTOMATION);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onStatusFlowChanged(ConfigEvents.StatusFlowChanged event) {
        onboarding.tick(event.organizationId(), event.actorId(), OnboardingService.ITEM_CUSTOMIZE_FLOW);
    }

    private void tick(UUID actorId, String item, String ruleName) {
        if (actorId == null || ruleName != null) {
            return;
        }

        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        if (context == null || !context.hasOrganization()) {
            return;
        }

        onboarding.tick(context.organizationId(), actorId, item);
    }
}
