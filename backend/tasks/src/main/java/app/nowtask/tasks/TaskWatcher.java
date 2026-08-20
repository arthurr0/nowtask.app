package app.nowtask.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "task_watcher")
public class TaskWatcher {

    @Id
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "user_id")
    private UUID userId;

    protected TaskWatcher() {
    }

    TaskWatcher(UUID id, UUID taskId, UUID userId) {
        this.id = id;
        this.taskId = taskId;
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }
}
