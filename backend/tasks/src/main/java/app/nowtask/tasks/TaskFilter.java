package app.nowtask.tasks;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import app.nowtask.shared.FilterNode;
import app.nowtask.shared.Priority;
import app.nowtask.shared.StatusCategory;
import app.nowtask.shared.TaskField;
import app.nowtask.shared.TaskQuery;

record TaskFilter(String where, List<Object> params) {

    record Context(UUID currentUserId, String currentSprint, LocalDate today, Map<String, String> customTypes) {
    }

    enum Kind { TEXT, REF, LABELS, DATE, NUMBER, BOOLEAN }

    private record Field(Kind kind, String sql, boolean nullable) {
    }

    private static final Map<String, Field> FIELDS = Map.ofEntries(
            Map.entry("title", new Field(Kind.TEXT, "t.title", false)),
            Map.entry("key", new Field(Kind.TEXT, "t.task_key", false)),
            Map.entry("description", new Field(Kind.TEXT, "t.description", true)),
            Map.entry("status", new Field(Kind.REF, "t.status_id::TEXT", false)),
            Map.entry("statusCategory", new Field(Kind.REF, "s.category", false)),
            Map.entry("project", new Field(Kind.REF, "t.project_id::TEXT", false)),
            Map.entry("epic", new Field(Kind.REF, "t.epic_id::TEXT", true)),
            Map.entry("sprint", new Field(Kind.REF, "t.sprint_code", true)),
            Map.entry("assignee", new Field(Kind.REF, "t.assignee_id::TEXT", true)),
            Map.entry("reviewer", new Field(Kind.REF, "t.reviewer_id::TEXT", true)),
            Map.entry("priority", new Field(Kind.REF, "t.priority", false)),
            Map.entry("labels", new Field(Kind.LABELS, "", true)),
            Map.entry("dueDate", new Field(Kind.DATE, "t.due_date", true)),
            Map.entry("startDate", new Field(Kind.DATE, "t.start_date", true)),
            Map.entry("endDate", new Field(Kind.DATE, "t.end_date", true)),
            Map.entry("createdAt", new Field(Kind.DATE, "t.created_at::DATE", false)),
            Map.entry("updatedAt", new Field(Kind.DATE, "t.updated_at::DATE", false)),
            Map.entry("completedAt", new Field(Kind.DATE, "t.completed_at::DATE", true)),
            Map.entry("estimate", new Field(Kind.NUMBER, "t.estimate", true)),
            Map.entry("progress", new Field(Kind.NUMBER, "t.progress", false)),
            Map.entry("automated", new Field(Kind.BOOLEAN, "t.automated", false)));

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("manual", "t.task_key"),
            Map.entry("key", "t.task_key"),
            Map.entry("title", "t.title"),
            Map.entry("status", "s.position"),
            Map.entry("priority", "CASE t.priority WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END"),
            Map.entry("due", "t.due_date"),
            Map.entry("dueDate", "t.due_date"),
            Map.entry("startDate", "t.start_date"),
            Map.entry("endDate", "t.end_date"),
            Map.entry("estimate", "t.estimate"),
            Map.entry("progress", "t.progress"),
            Map.entry("created", "t.created_at"),
            Map.entry("updated", "t.updated_at"));

    private static final Pattern RELATIVE = Pattern.compile("^([+-]?)(\\d+)([dwm])$");

    static TaskFilter of(TaskQuery source, Context context) {
        TaskQuery query = (source == null ? TaskQuery.empty() : source).normalized();
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (hasText(query.query())) {
            clauses.add("(t.title ILIKE ? OR t.task_key ILIKE ?)");
            String pattern = "%" + query.query().trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (query.projectId() != null) {
            clauses.add("t.project_id = ?");
            params.add(query.projectId());
        }

        String tree = node(query.filter(), context, params);
        if (tree != null) {
            clauses.add(tree);
        }

        return new TaskFilter(
                clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses),
                List.copyOf(params));
    }

    static String orderBy(String sort) {
        if (!hasText(sort)) {
            return " ORDER BY t.task_key";
        }

        boolean descending = sort.startsWith("-");
        String field = descending ? sort.substring(1) : sort;
        String column = SORTABLE.get(field);

        if (column == null) {
            throw new IllegalArgumentException("Nieznane sortowanie: " + sort);
        }

        return " ORDER BY " + column + (descending ? " DESC" : " ASC") + " NULLS LAST, t.task_key";
    }

    private static String node(FilterNode node, Context context, List<Object> params) {
        if (node == null) {
            return null;
        }
        if (!node.isGroup()) {
            return condition(node, context, params);
        }
        List<String> parts = new ArrayList<>();
        for (FilterNode child : node.children()) {
            String sql = node(child, context, params);
            if (sql != null) {
                parts.add(sql);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return "(" + String.join(node.isOr() ? " OR " : " AND ", parts) + ")";
    }

    private static String condition(FilterNode node, Context context, List<Object> params) {
        if (!hasText(node.field())) {
            return null;
        }
        Field field = resolve(node.field(), context);
        String op = node.op() == null ? "" : node.op();
        List<String> values = node.valueList().stream().filter(TaskFilter::hasText).map(String::trim).toList();

        return switch (field.kind()) {
            case TEXT -> text(field, op, values, params);
            case REF -> ref(node.field(), field, op, values, context, params);
            case LABELS -> labels(op, values, params);
            case DATE -> date(field, op, values, context, params);
            case NUMBER -> number(field, op, values, params);
            case BOOLEAN -> bool(field, op, values);
        };
    }

    private static Field resolve(String key, Context context) {
        Field known = FIELDS.get(key);
        if (known != null) {
            return known;
        }
        if (TaskField.isCustom(key)) {
            String fieldKey = TaskField.customFieldKey(key);
            String type = context.customTypes().get(fieldKey);
            if (type == null) {
                throw new IllegalArgumentException("Nieznane pole własne: " + fieldKey);
            }
            String raw = "NULLIF(t.custom ->> '" + fieldKey.replace("'", "''") + "', '')";
            return switch (type) {
                case "number", "currency", "formula" -> new Field(Kind.NUMBER, "(" + raw + ")::NUMERIC", true);
                case "date" -> new Field(Kind.DATE, "(" + raw + ")::DATE", true);
                case "toggle" -> new Field(Kind.BOOLEAN, "COALESCE((" + raw + ")::BOOLEAN, FALSE)", false);
                case "select", "person" -> new Field(Kind.REF, raw, true);
                default -> new Field(Kind.TEXT, raw, true);
            };
        }
        throw new IllegalArgumentException("Nieznane pole filtra: " + key);
    }

    private static String text(Field field, String op, List<String> values, List<Object> params) {
        switch (op) {
            case "isEmpty" -> {
                return "COALESCE(" + field.sql() + ", '') = ''";
            }
            case "isNotEmpty" -> {
                return "COALESCE(" + field.sql() + ", '') <> ''";
            }
            default -> {
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        String value = values.getFirst();
        return switch (op) {
            case "contains" -> {
                params.add("%" + value + "%");
                yield field.sql() + " ILIKE ?";
            }
            case "notContains" -> {
                params.add("%" + value + "%");
                yield "COALESCE(" + field.sql() + ", '') NOT ILIKE ?";
            }
            case "is" -> {
                params.add(value);
                yield "LOWER(" + field.sql() + ") = LOWER(?)";
            }
            case "isNot" -> {
                params.add(value);
                yield "LOWER(COALESCE(" + field.sql() + ", '')) <> LOWER(?)";
            }
            default -> throw unknownOp(op);
        };
    }

    private static String ref(
            String key, Field field, String op, List<String> values, Context context, List<Object> params) {
        switch (op) {
            case "isEmpty" -> {
                return field.sql() + " IS NULL";
            }
            case "isNotEmpty" -> {
                return field.sql() + " IS NOT NULL";
            }
            default -> {
            }
        }
        List<String> resolved = values.stream().map(value -> refValue(key, value, context)).toList();
        if (resolved.isEmpty()) {
            return null;
        }
        String placeholders = String.join(", ", resolved.stream().map(value -> "?").toList());
        params.addAll(resolved);
        return switch (op) {
            case "in", "is" -> field.sql() + " IN (" + placeholders + ")";
            case "notIn", "isNot" -> "(" + field.sql() + " IS NULL OR " + field.sql() + " NOT IN (" + placeholders + "))";
            default -> throw unknownOp(op);
        };
    }

    private static String refValue(String key, String value, Context context) {
        return switch (key) {
            case "assignee", "reviewer" -> "me".equals(value)
                    ? (context.currentUserId() == null ? "" : context.currentUserId().toString())
                    : value;
            case "sprint" -> "current".equals(value)
                    ? (context.currentSprint() == null ? "" : context.currentSprint())
                    : value;
            case "priority" -> Priority.of(value).code();
            case "statusCategory" -> StatusCategory.of(value).code();
            case "status", "project", "epic" -> UUID.fromString(value).toString();
            default -> TaskField.isCustom(key) && "me".equals(value) && context.currentUserId() != null
                    ? context.currentUserId().toString()
                    : value;
        };
    }

    private static String labels(String op, List<String> values, List<Object> params) {
        String exists = "EXISTS (SELECT 1 FROM task_label tl WHERE tl.task_id = t.id";
        switch (op) {
            case "isEmpty" -> {
                return "NOT " + exists + ")";
            }
            case "isNotEmpty" -> {
                return exists + ")";
            }
            default -> {
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        String placeholders = String.join(", ", values.stream().map(value -> "?").toList());
        return switch (op) {
            case "in", "is" -> {
                params.addAll(values);
                yield exists + " AND tl.label IN (" + placeholders + "))";
            }
            case "notIn", "isNot" -> {
                params.addAll(values);
                yield "NOT " + exists + " AND tl.label IN (" + placeholders + "))";
            }
            case "all" -> {
                List<String> parts = new ArrayList<>();
                for (String value : values) {
                    params.add(value);
                    parts.add(exists + " AND tl.label = ?)");
                }
                yield "(" + String.join(" AND ", parts) + ")";
            }
            default -> throw unknownOp(op);
        };
    }

    private static String date(Field field, String op, List<String> values, Context context, List<Object> params) {
        switch (op) {
            case "isEmpty" -> {
                return field.sql() + " IS NULL";
            }
            case "isNotEmpty" -> {
                return field.sql() + " IS NOT NULL";
            }
            default -> {
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        LocalDate first = resolveDate(values.getFirst(), context.today());
        return switch (op) {
            case "on", "is" -> {
                params.add(first);
                yield field.sql() + " = ?";
            }
            case "before" -> {
                params.add(first);
                yield field.sql() + " < ?";
            }
            case "after" -> {
                params.add(first);
                yield field.sql() + " > ?";
            }
            case "onOrBefore" -> {
                params.add(first);
                yield field.sql() + " <= ?";
            }
            case "onOrAfter" -> {
                params.add(first);
                yield field.sql() + " >= ?";
            }
            case "between" -> {
                if (values.size() < 2) {
                    yield null;
                }
                params.add(first);
                params.add(resolveDate(values.get(1), context.today()));
                yield field.sql() + " BETWEEN ? AND ?";
            }
            default -> throw unknownOp(op);
        };
    }

    private static String number(Field field, String op, List<String> values, List<Object> params) {
        switch (op) {
            case "isEmpty" -> {
                return field.sql() + " IS NULL";
            }
            case "isNotEmpty" -> {
                return field.sql() + " IS NOT NULL";
            }
            default -> {
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        double first = parseNumber(values.getFirst());
        return switch (op) {
            case "eq", "is" -> {
                params.add(first);
                yield field.sql() + " = ?";
            }
            case "ne", "isNot" -> {
                params.add(first);
                yield "(" + field.sql() + " IS NULL OR " + field.sql() + " <> ?)";
            }
            case "gt" -> {
                params.add(first);
                yield field.sql() + " > ?";
            }
            case "gte" -> {
                params.add(first);
                yield field.sql() + " >= ?";
            }
            case "lt" -> {
                params.add(first);
                yield field.sql() + " < ?";
            }
            case "lte" -> {
                params.add(first);
                yield field.sql() + " <= ?";
            }
            case "between" -> {
                if (values.size() < 2) {
                    yield null;
                }
                params.add(first);
                params.add(parseNumber(values.get(1)));
                yield field.sql() + " BETWEEN ? AND ?";
            }
            default -> throw unknownOp(op);
        };
    }

    private static String bool(Field field, String op, List<String> values) {
        boolean wanted = values.isEmpty() || !"false".equalsIgnoreCase(values.getFirst());
        return switch (op) {
            case "is" -> wanted ? field.sql() : "NOT " + field.sql();
            case "isNot" -> wanted ? "NOT " + field.sql() : field.sql();
            default -> throw unknownOp(op);
        };
    }

    static LocalDate resolveDate(String value, LocalDate today) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if ("today".equals(trimmed)) {
            return today;
        }
        Matcher matcher = RELATIVE.matcher(trimmed);
        if (matcher.matches()) {
            int amount = Integer.parseInt(matcher.group(2));
            if ("-".equals(matcher.group(1))) {
                amount = -amount;
            }
            return switch (matcher.group(3)) {
                case "w" -> today.plusWeeks(amount);
                case "m" -> today.plusMonths(amount);
                default -> today.plusDays(amount);
            };
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }

    private static double parseNumber(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    private static IllegalArgumentException unknownOp(String op) {
        return new IllegalArgumentException("Nieznany operator filtra: " + op);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
