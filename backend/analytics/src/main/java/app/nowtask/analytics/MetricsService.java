package app.nowtask.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.automation.api.AutomationViews.RuleUsage;
import app.nowtask.automation.api.Automations;
import app.nowtask.tasks.api.Tasks;

@Service
@Transactional(readOnly = true)
public class MetricsService {
    private final Tasks tasks;
    private final Automations automations;
    private final JdbcClient jdbc;

    MetricsService(Tasks tasks, Automations automations, JdbcClient jdbc) {
        this.tasks = tasks;
        this.automations = automations;
        this.jdbc = jdbc;
    }

    public record BurndownPointView(LocalDate day, Integer remaining, int ideal) {
    }

    public record ThroughputWeekView(String label, int completed) {
    }

    public record WorkloadRowView(UUID userId, int points, int capacity) {
    }

    public record Overview(
            int tasksInProgress,
            int completedThisWeek,
            Double averageCycleTimeDays,
            int ruleExecutions,
            int ruleErrors,
            List<BurndownPointView> burndown,
            List<ThroughputWeekView> throughput,
            List<WorkloadRowView> workload,
            List<RuleUsage> ruleUsage,
            int periodDays) {
    }

    public Overview overview() {
        return overview(7);
    }

    public Overview overview(int days) {
        int window = days <= 0 ? 7 : Math.min(days, 365);
        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);
        List<RuleUsage> usage = automations.usage();

        return new Overview(
                tasks.countInProgress(),
                tasks.countCompletedSince(since),
                tasks.averageCycleTimeDays(),
                usage.stream().mapToInt(RuleUsage::runs).sum(),
                (int) usage.stream().filter(RuleUsage::failing).count(),
                burndown(),
                throughput(),
                workload(),
                usage,
                window);
    }

    private List<BurndownPointView> burndown() {
        return jdbc.sql("""
                        SELECT day, remaining, ideal
                        FROM burndown_point
                        ORDER BY day
                        """)
                .query((rs, rowNum) -> new BurndownPointView(
                        rs.getObject("day", LocalDate.class),
                        rs.getObject("remaining") == null ? null : rs.getInt("remaining"),
                        rs.getInt("ideal")))
                .list();
    }

    private List<ThroughputWeekView> throughput() {
        return jdbc.sql("SELECT label, completed FROM throughput_week ORDER BY week_start")
                .query((rs, rowNum) -> new ThroughputWeekView(rs.getString("label"), rs.getInt("completed")))
                .list();
    }

    private List<WorkloadRowView> workload() {
        return tasks.workloadByAssignee().stream()
                .map(row -> new WorkloadRowView(row.userId(), row.points(), row.capacity()))
                .toList();
    }
}
