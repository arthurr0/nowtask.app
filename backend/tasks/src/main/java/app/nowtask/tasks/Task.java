package app.nowtask.tasks;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import app.nowtask.shared.Priority;

@Entity
@Table(name = "task")
public class Task {

    @Id
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "epic_id")
    private UUID epicId;

    @Column(name = "task_key")
    private String key;

    private String title;

    private String description;

    @Column(name = "status_id")
    private UUID statusId;

    private String priority;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "sprint_code")
    private String sprintCode;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private Integer estimate;

    private int progress;

    @Column(name = "attachment_count")
    private int attachmentCount;

    private boolean automated;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom")
    private Map<String, Object> custom = new LinkedHashMap<>();

    @ElementCollection
    @CollectionTable(name = "task_label", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "label")
    private Set<String> labels = new LinkedHashSet<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Task() {
    }

    Task(UUID id, UUID projectId, String key, String title, UUID statusId, String sprintCode) {
        this.id = id;
        this.projectId = projectId;
        this.key = key;
        this.title = title;
        this.statusId = statusId;
        this.sprintCode = sprintCode;
        this.description = "";
        this.priority = Priority.MEDIUM.code();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEpicId() {
        return epicId;
    }

    public void setEpicId(UUID epicId) {
        this.epicId = epicId;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getStatusId() {
        return statusId;
    }

    public void setStatusId(UUID statusId) {
        this.statusId = statusId;
    }

    public Priority getPriority() {
        return Priority.of(priority);
    }

    public void setPriority(Priority priority) {
        this.priority = priority.code();
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public void setSprintCode(String sprintCode) {
        this.sprintCode = sprintCode;
    }

    public String getSprintCode() {
        return sprintCode;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getEstimate() {
        return estimate;
    }

    public void setEstimate(Integer estimate) {
        this.estimate = estimate;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public boolean isAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }

    public Map<String, Object> getCustom() {
        return custom;
    }

    public void setCustom(Map<String, Object> custom) {
        this.custom = new LinkedHashMap<>(custom);
    }

    public Set<String> getLabels() {
        return labels;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
