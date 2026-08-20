package app.nowtask.automation;

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
@Table(name = "automation_run")
public class AutomationRun {
    @Id
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "task_key")
    private String taskKey;

    private String outcome;

    @Column(name = "detail_key")
    private String detailKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_params")
    private Map<String, Object> detailParams;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AutomationRun() {
    }

    AutomationRun(UUID id, UUID ruleId, String taskKey, String outcome, String detailKey, Map<String, Object> detailParams) {
        this.id = id;
        this.ruleId = ruleId;
        this.taskKey = taskKey;
        this.outcome = outcome;
        this.detailKey = detailKey;
        this.detailParams = detailParams;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetailKey() {
        return detailKey;
    }

    public Map<String, Object> getDetailParams() {
        return detailParams;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
