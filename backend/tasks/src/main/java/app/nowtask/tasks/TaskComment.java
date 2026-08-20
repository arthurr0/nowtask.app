package app.nowtask.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comment")
public class TaskComment {

    @Id
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "author_id")
    private UUID authorId;

    private String body;

    @Column(name = "created_at")
    private Instant createdAt;

    protected TaskComment() {
    }

    TaskComment(UUID id, UUID taskId, UUID authorId, String body, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
