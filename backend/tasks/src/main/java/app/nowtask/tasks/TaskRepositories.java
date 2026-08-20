package app.nowtask.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByKey(String key);

    List<Task> findBySprintCodeOrderByKeyAsc(String sprintCode);

    List<Task> findByKeyIn(List<String> keys);
}

interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    List<Subtask> findByTaskIdOrderByPositionAsc(UUID taskId);

    List<Subtask> findByTaskIdIn(List<UUID> taskIds);
}

interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    long countByTaskId(UUID taskId);
}

interface TaskHistoryRepository extends JpaRepository<TaskHistoryEntry, UUID> {

    List<TaskHistoryEntry> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}

interface TaskWatcherRepository extends JpaRepository<TaskWatcher, UUID> {

    Optional<TaskWatcher> findByTaskIdAndUserId(UUID taskId, UUID userId);

    int countByTaskId(UUID taskId);
}
