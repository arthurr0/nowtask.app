package app.nowtask.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_history")
public class TaskHistoryEntry {

    @Id
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "created_at")
    private Instant createdAt;

    protected TaskHistoryEntry() {
    }

    TaskHistoryEntry(UUID id, UUID taskId, String field, String oldValue, String newValue, UUID actorId, String ruleName) {
        this.id = id;
        this.taskId = taskId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actorId = actorId;
        this.ruleName = ruleName;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
