package app.nowtask.integrations;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.integrations.api.Channels.Delivery;

@Component
class DeliveryLog {

    private final IntegrationRepository integrations;
    private final IntegrationDeliveryRepository deliveries;

    DeliveryLog(IntegrationRepository integrations, IntegrationDeliveryRepository deliveries) {
        this.integrations = integrations;
        this.deliveries = deliveries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(UUID integrationId, String event, String taskKey, Delivery delivery) {
        deliveries.save(new IntegrationDelivery(
                integrationId, event, taskKey, delivery.ok(), delivery.detail()));

        integrations.findById(integrationId)
                .ifPresent(integration -> integration.recordAttempt(delivery.ok(), delivery.detail()));
    }
}
