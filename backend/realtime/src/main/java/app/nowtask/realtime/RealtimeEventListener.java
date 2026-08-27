package app.nowtask.realtime;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import app.nowtask.shared.events.ConfigEvents;
import app.nowtask.shared.events.RealtimeEvents;

@Component
class RealtimeEventListener {

    private final RealtimeBroker broker;

    RealtimeEventListener(RealtimeBroker broker) {
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onTaskChanged(RealtimeEvents.TaskChanged event) {
        broker.publish(
                event.organizationId(),
                RealtimeMessage.task(event.taskKey(), event.change(), event.actorId(), event.at()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onWorkspaceChanged(RealtimeEvents.WorkspaceChanged event) {
        broker.publish(
                event.organizationId(),
                RealtimeMessage.workspace(event.change(), event.actorId(), event.at()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onNotificationPosted(RealtimeEvents.NotificationPosted event) {
        broker.publishToUser(
                event.organizationId(),
                event.userId(),
                RealtimeMessage.notification(event.taskKey(), event.at()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStatusFlowChanged(ConfigEvents.StatusFlowChanged event) {
        broker.publish(
                event.organizationId(),
                RealtimeMessage.workspace("statuses", event.actorId(), Instant.now()));
    }
}
