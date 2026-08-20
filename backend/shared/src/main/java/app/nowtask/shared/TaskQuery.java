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
        String groupBy,
        String sort,
        List<String> columns,
        Integer page,
        Integer size) {

    public static final List<String> COLUMN_CODES = List.of("labels", "assignee", "priority", "due", "estimate");

    public static TaskQuery empty() {
        return new TaskQuery(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static TaskQuery ofText(String text, int size) {
        return new TaskQuery(
                text, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, size);
    }

    public TaskQuery withCheckedColumns() {
        if (columns == null) {
            return this;
        }

        List<String> checked = new ArrayList<>(columns.size());
        for (String code : columns) {
            if (code == null || !COLUMN_CODES.contains(code)) {
                throw new IllegalArgumentException("Nieznana kolumna listy: " + code);
            }
            if (!checked.contains(code)) {
                checked.add(code);
            }
        }

        return new TaskQuery(query, statusId, assigneeId, label, priority, epicId, projectId, dueBefore,
                unassigned, automated, sprint, groupBy, sort, List.copyOf(checked), page, size);
    }

    public boolean hasFilters() {
        return query != null
                || statusId != null
                || assigneeId != null
                || label != null
                || priority != null
                || epicId != null
                || projectId != null
                || dueBefore != null
                || unassigned != null
                || automated != null
                || sprint != null;
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
}
