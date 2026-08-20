package app.nowtask.tasks.api;

import java.util.List;
import java.util.UUID;
import app.nowtask.tasks.api.TaskViews.ScheduledTask;
import app.nowtask.tasks.api.TaskViews.TaskSummary;

public interface Tasks {

    List<TaskSummary> currentSprint();

    List<String> sprints();

    List<ScheduledTask> scheduled();

    List<WorkloadRow> workloadByAssignee();

    int countInProgress();

    int countCompletedSince(java.time.Instant since);

    Double averageCycleTimeDays();

    record WorkloadRow(UUID userId, int points, int capacity) {
    }
}
