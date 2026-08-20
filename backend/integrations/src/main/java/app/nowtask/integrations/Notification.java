package app.nowtask.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification")
class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private Instant at;

    private String kind;

    @Column(name = "title_key")
    private String titleKey;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> params;

    @Column(name = "task_key")
    private String taskKey;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    Notification(UUID userId, String kind, String titleKey, Map<String, Object> params, String taskKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.at = Instant.now();
        this.kind = kind;
        this.titleKey = titleKey;
        this.params = params == null ? Map.of() : params;
        this.taskKey = taskKey;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getAt() {
        return at;
    }

    String getKind() {
        return kind;
    }

    String getTitleKey() {
        return titleKey;
    }

    Map<String, Object> getParams() {
        return params == null ? Map.of() : params;
    }

    String getTaskKey() {
        return taskKey;
    }

    boolean isRead() {
        return readAt != null;
    }

    void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
