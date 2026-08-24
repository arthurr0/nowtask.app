package app.nowtask.integrations;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.events.TaskEvents;

@Component
class IntegrationEventListener {

    private final NotificationService notifications;
    private final IntegrationService integrations;
    private final UserDirectory users;
    private final AsyncTaskExecutor executor;

    IntegrationEventListener(
            NotificationService notifications,
            IntegrationService integrations,
            UserDirectory users,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.notifications = notifications;
        this.integrations = integrations;
        this.users = users;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onCreated(TaskEvents.TaskCreated event) {
        send(IntegrationService.EVENT_TASK_CREATED, event.taskKey(), event.title());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onAssigned(TaskEvents.TaskAssigned event) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskKey", event.taskKey());

        notifications.create(event.assigneeId(), "assigned", "notify.assigned", params, event.taskKey());
        send(IntegrationService.EVENT_TASK_ASSIGNED, event.taskKey(),
                users.findById(event.assigneeId()).map(UserView::name).orElse(""));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onStatusChanged(TaskEvents.TaskStatusChanged event) {
        send(IntegrationService.EVENT_TASK_STATUS_CHANGED, event.taskKey(),
                event.fromStatusLabel() + " \u2192 " + event.toStatusLabel());
    }

    private void send(String event, String taskKey, String message) {
        OrganizationContext scope = OrganizationContextHolder.currentOrNull();
        if (scope == null || !scope.hasOrganization()) {
            return;
        }

        executor.execute(
                () -> OrganizationContextHolder.runAs(scope, () -> integrations.dispatch(event, taskKey, message)));
    }
}
