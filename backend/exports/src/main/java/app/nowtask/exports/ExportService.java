package app.nowtask.exports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import app.nowtask.automation.api.AutomationViews.RunView;
import app.nowtask.automation.api.Automations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.TaskQuery;
import app.nowtask.tasks.TaskQueryService;
import app.nowtask.tasks.api.TaskViews.TaskSummary;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.StatusView;

@Service
public class ExportService {

    public record ExportFile(String filename, String contentType, byte[] content) {
    }

    private static final int MAX_ROWS = 10_000;
    private static final String CSV = "csv";
    private static final String XLSX = "xlsx";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final TaskQueryService tasks;
    private final Workspace workspace;
    private final UserDirectory users;
    private final Automations automations;

    ExportService(
            TaskQueryService tasks,
            Workspace workspace,
            UserDirectory users,
            Automations automations) {
        this.tasks = tasks;
        this.workspace = workspace;
        this.users = users;
        this.automations = automations;
    }

    public ExportFile tasks(TaskQuery source, String format) {
        String kind = checkedFormat(format, true);
        TaskQuery query = capped(source == null ? TaskQuery.empty() : source);

        List<String> columns = visibleColumns(query);
        List<String> header = header(columns);
        List<List<String>> rows = rows(query, columns);

        return CSV.equals(kind)
                ? csv("nowtask-tasks", header, rows)
                : xlsx("nowtask-tasks", "Tasks", header, rows);
    }

    public ExportFile ruleRuns(UUID ruleId, String format) {
        checkedFormat(format, false);

        List<RunView> runs = ruleId == null ? automations.recentRuns() : automations.runsOf(ruleId);
        List<String> header = List.of("Rule", "Task", "Outcome", "Details", "When");

        List<List<String>> rows = runs.stream()
                .map(run -> List.of(
                        text(run.ruleName()),
                        text(run.taskKey()),
                        text(run.outcome()),
                        text(run.detailKey()),
                        run.createdAt() == null ? "" : run.createdAt().atZone(ZONE).toLocalDateTime().toString()))
                .toList();

        return csv("nowtask-rule-runs", header, rows);
    }

    private List<String> visibleColumns(TaskQuery query) {
        List<String> columns = query.columns();
        List<String> chosen = columns == null || columns.isEmpty() ? TaskQuery.COLUMN_CODES : columns;
        return chosen.stream().filter(code -> !"status".equals(code)).toList();
    }

    private List<String> header(List<String> columns) {
        List<String> header = new ArrayList<>(List.of("Key", "Title", "Status"));
        for (String column : columns) {
            header.add(switch (column) {
                case "labels" -> "Labels";
                case "assignee" -> "Assignee";
                case "priority" -> "Priority";
                case "due" -> "Due date";
                case "estimate" -> "Estimate";
                default -> column;
            });
        }
        return header;
    }

    private List<List<String>> rows(TaskQuery query, List<String> columns) {
        List<TaskSummary> items = tasks.page(query).items();

        Map<UUID, StatusView> statuses = workspace.statuses().stream()
                .collect(Collectors.toMap(StatusView::id, Function.identity(), (first, second) -> first));
        Map<UUID, UserView> people = users.findAll().stream()
                .collect(Collectors.toMap(UserView::id, Function.identity(), (first, second) -> first));

        List<List<String>> rows = new ArrayList<>(items.size());
        for (TaskSummary task : items) {
            List<String> row = new ArrayList<>();
            row.add(text(task.key()));
            row.add(text(task.title()));
            row.add(statusLabel(statuses, task));

            for (String column : columns) {
                row.add(switch (column) {
                    case "labels" -> String.join(", ", task.labels() == null ? List.of() : task.labels());
                    case "assignee" -> task.assigneeId() == null || people.get(task.assigneeId()) == null
                            ? ""
                            : people.get(task.assigneeId()).name();
                    case "priority" -> task.priority() == null ? "" : task.priority().code();
                    case "due" -> task.dueDate() == null ? "" : task.dueDate().toString();
                    case "estimate" -> task.estimate() == null ? "" : task.estimate().toString();
                    default -> "";
                });
            }
            rows.add(row);
        }
        return rows;
    }

    private String statusLabel(Map<UUID, StatusView> statuses, TaskSummary task) {
        StatusView status = task.statusId() == null ? null : statuses.get(task.statusId());
        return status == null ? text(task.statusCode()) : status.label();
    }

    private TaskQuery capped(TaskQuery query) {
        return query.withCheckedColumns().withPaging(0, MAX_ROWS);
    }

    private ExportFile csv(String name, List<String> header, List<List<String>> rows) {
        Csv csv = new Csv();
        csv.row(header);
        rows.forEach(csv::row);
        return new ExportFile(filename(name, CSV), "text/csv; charset=utf-8", csv.bytes());
    }

    private ExportFile xlsx(String name, String sheetName, List<String> header, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);

            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < header.size(); index++) {
                headerRow.createCell(index).setCellValue(header.get(index));
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> cells = rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                    Cell cell = row.createCell(cellIndex);
                    cell.setCellValue(cells.get(cellIndex));
                }
            }

            for (int index = 0; index < header.size(); index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(out);
            return new ExportFile(
                    filename(name, XLSX),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String checkedFormat(String format, boolean allowXlsx) {
        String kind = format == null || format.isBlank() ? CSV : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (CSV.equals(kind)) {
            return CSV;
        }
        if (XLSX.equals(kind) && allowXlsx) {
            return XLSX;
        }
        throw new RuleViolationException(allowXlsx
                ? "The export format has to be one of: csv, xlsx"
                : "This export is available in csv format only");
    }

    private static String filename(String name, String extension) {
        return name + "-" + LocalDate.now() + "." + extension;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
