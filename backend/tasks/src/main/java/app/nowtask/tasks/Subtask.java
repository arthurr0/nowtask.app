package app.nowtask.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "subtask")
public class Subtask {

    @Id
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    private String title;

    private boolean done;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    private int position;

    protected Subtask() {
    }

    Subtask(UUID id, UUID taskId, String title, UUID assigneeId, int position) {
        this.id = id;
        this.taskId = taskId;
        this.title = title;
        this.assigneeId = assigneeId;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public int getPosition() {
        return position;
    }
}
