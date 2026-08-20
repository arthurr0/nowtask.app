package app.nowtask.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "integration")
class Integration {

    @Id
    private UUID id;

    private String kind;

    private String name;

    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> config;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_at")
    private Instant lastAt;

    @Column(name = "last_detail")
    private String lastDetail;

    protected Integration() {
    }

    Integration(String kind, String name, Map<String, Object> config) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.name = name;
        this.enabled = true;
        this.config = config == null ? Map.of() : config;
        this.createdAt = Instant.now();
        this.lastDetail = "";
    }

    UUID getId() {
        return id;
    }

    String getKind() {
        return kind;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    Map<String, Object> getConfig() {
        return config == null ? Map.of() : config;
    }

    void setConfig(Map<String, Object> config) {
        this.config = config == null ? Map.of() : new LinkedHashMap<>(config);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getLastStatus() {
        return lastStatus;
    }

    Instant getLastAt() {
        return lastAt;
    }

    String getLastDetail() {
        return lastDetail == null ? "" : lastDetail;
    }

    void recordAttempt(boolean ok, String detail) {
        this.lastStatus = ok ? "ok" : "error";
        this.lastAt = Instant.now();
        this.lastDetail = detail == null ? "" : detail;
    }

    String text(String key) {
        Object value = getConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }

    List<String> events() {
        Object value = getConfig().get("events");
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    boolean listensTo(String event) {
        List<String> events = events();
        return events.isEmpty() || events.contains(event);
    }
}
