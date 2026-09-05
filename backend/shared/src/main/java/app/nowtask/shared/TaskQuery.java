package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskQuery(
        String query,
        UUID statusId,
        UUID assigneeId,
        String label,
        String priority,
        UUID epicId,
        UUID projectId,
        String dueBefore,
        Boolean unassigned,
        Boolean automated,
        String sprint,
        FilterNode filter,
        String layout,
        String groupBy,
        String sort,
        List<String> columns,
        Integer page,
        Integer size) {

    public static final List<String> COLUMN_CODES = List.of("status", "labels", "assignee", "priority", "due", "estimate");
    public static final List<String> LAYOUT_CODES = List.of("board", "list", "timeline", "calendar");

    public static TaskQuery empty() {
        return new TaskQuery(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    public static TaskQuery ofText(String text, int size) {
        return new TaskQuery(
                text, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                0, size);
    }

    public TaskQuery withCheckedColumns() {
        List<String> checked = null;
        if (columns != null) {
            checked = new ArrayList<>(columns.size());
            for (String code : columns) {
                if (code == null || !COLUMN_CODES.contains(code)) {
                    throw new IllegalArgumentException("Nieznana kolumna listy: " + code);
                }
                if (!checked.contains(code)) {
                    checked.add(code);
                }
            }
            checked = List.copyOf(checked);
        }
        if (layout != null && !layout.isBlank() && !LAYOUT_CODES.contains(layout)) {
            throw new IllegalArgumentException("Nieznany układ widoku: " + layout);
        }
        return new TaskQuery(query, statusId, assigneeId, label, priority, epicId, projectId, dueBefore,
                unassigned, automated, sprint, filter, blankToNull(layout), groupBy, sort, checked, page, size);
    }

    public TaskQuery normalized() {
        List<FilterNode> legacy = new ArrayList<>();
        if (statusId != null) {
            legacy.add(FilterNode.condition("status", "in", statusId.toString()));
        }
        if (assigneeId != null) {
            legacy.add(FilterNode.condition("assignee", "in", assigneeId.toString()));
        }
        if (hasText(label)) {
            legacy.add(FilterNode.condition("labels", "in", label.trim()));
        }
        if (hasText(priority)) {
            legacy.add(FilterNode.condition("priority", "in", priority.trim()));
        }
        if (epicId != null) {
            legacy.add(FilterNode.condition("epic", "in", epicId.toString()));
        }
        if (hasText(dueBefore)) {
            legacy.add(FilterNode.condition("dueDate", "before", dueBefore.trim()));
        }
        if (unassigned != null) {
            legacy.add(FilterNode.condition("assignee", unassigned ? "isEmpty" : "isNotEmpty", List.of()));
        }
        if (automated != null) {
            legacy.add(FilterNode.condition("automated", "is", automated ? "true" : "false"));
        }
        if (hasText(sprint)) {
            legacy.add(FilterNode.condition("sprint", "in", sprint.trim()));
        }

        FilterNode base = filter == null ? FilterNode.all() : filter;
        FilterNode merged = base.prepend(legacy);

        return new TaskQuery(query, null, null, null, null, null, projectId, null, null, null, null,
                merged, layout, groupBy, sort, columns, page, size);
    }

    public TaskQuery withPaging(Integer page, Integer size) {
        return new TaskQuery(query, statusId, assigneeId, label, priority, epicId, projectId, dueBefore,
                unassigned, automated, sprint, filter, layout, null, sort, columns, page, size);
    }

    public boolean paged() {
        return size != null && size > 0;
    }

    public int pageNumber() {
        return page == null || page < 0 ? 0 : page;
    }

    public int pageSize() {
        return paged() ? size : 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
