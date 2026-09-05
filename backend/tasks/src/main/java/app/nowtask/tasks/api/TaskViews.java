package app.nowtask.tasks.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import app.nowtask.shared.Priority;

public final class TaskViews {

    private TaskViews() {
    }

    public record TaskSummary(
            UUID id,
            String key,
            String title,
            UUID statusId,
            String statusCode,
            Priority priority,
            UUID assigneeId,
            List<String> labels,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            Integer estimate,
            int progress,
            int subtasksDone,
            int subtasksTotal,
            int commentCount,
            boolean automated,
            UUID epicId,
            UUID projectId,
            String sprintCode,
            UUID reviewerId,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Map<String, Object> custom) {
    }

    public record SubtaskView(UUID id, String title, boolean done, UUID assigneeId) {
    }

    public record RelationView(String kind, String taskKey) {
    }

    public record CommentView(UUID id, UUID authorId, String body, Instant createdAt) {
    }

    public record HistoryView(
            UUID id,
            String field,
            String oldValue,
            String newValue,
            UUID actorId,
            String ruleName,
            Instant createdAt) {
    }

    public record TaskDetail(
            TaskSummary summary,
            String description,
            UUID reviewerId,
            int attachmentCount,
            int watcherCount,
            boolean watching,
            Map<String, Object> custom,
            List<SubtaskView> subtasks,
            List<RelationView> relations) {
    }

    public record TaskGroup(String key, String label, int count) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskPage(List<TaskSummary> items, int total, int page, int size, List<TaskGroup> groups) {
    }

    public record ScheduledTask(
            String key,
            String title,
            UUID epicId,
            UUID assigneeId,
            LocalDate startDate,
            LocalDate endDate,
            int progress,
            List<String> blocks) {
    }
}
