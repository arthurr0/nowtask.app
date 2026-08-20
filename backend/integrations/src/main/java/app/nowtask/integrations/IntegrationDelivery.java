package app.nowtask.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_delivery")
class IntegrationDelivery {

    @Id
    private UUID id;

    @Column(name = "integration_id")
    private UUID integrationId;

    private Instant at;

    private String event;

    @Column(name = "task_key")
    private String taskKey;

    private boolean ok;

    private String detail;

    protected IntegrationDelivery() {
    }

    IntegrationDelivery(UUID integrationId, String event, String taskKey, boolean ok, String detail) {
        this.id = UUID.randomUUID();
        this.integrationId = integrationId;
        this.at = Instant.now();
        this.event = event;
        this.taskKey = taskKey;
        this.ok = ok;
        this.detail = detail == null ? "" : detail;
    }

    UUID getId() {
        return id;
    }

    UUID getIntegrationId() {
        return integrationId;
    }

    Instant getAt() {
        return at;
    }

    String getEvent() {
        return event;
    }

    String getTaskKey() {
        return taskKey;
    }

    boolean isOk() {
        return ok;
    }

    String getDetail() {
        return detail == null ? "" : detail;
    }
}
