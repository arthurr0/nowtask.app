package app.nowtask.shared.events;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCommands {

    record TaskFacts(
            String taskKey,
            String title,
            String statusCode,
            String priority,
            List<String> labels,
            UUID assigneeId,
            UUID reviewerId,
            LocalDate dueDate,
            Integer estimate) {
    }

    Optional<TaskFacts> facts(String taskKey);

    List<TaskFacts> allFacts();

    void setStatus(String taskKey, String statusCode);

    void setPriority(String taskKey, String priority);

    void assign(String taskKey, UUID userId);

    void setReviewer(String taskKey, UUID userId);

    void addLabel(String taskKey, String label);

    void setDueInDays(String taskKey, int days);

    void addComment(String taskKey, String body);

    void setTitle(String taskKey, String title);

    void setDescription(String taskKey, String description);

    Optional<String> description(String taskKey);
}
