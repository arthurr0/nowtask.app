package app.nowtask.automation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.integrations.api.Channels;
import app.nowtask.shared.ActorContext;
import app.nowtask.shared.events.TaskCommands;
import app.nowtask.shared.events.TaskCommands.TaskFacts;

@Service
public class RuleEngine {

    public record ActionOutcome(String kind, String value, boolean applied, String detailKey) {
    }

    public record TaskOutcome(String taskKey, boolean matched, List<ActionOutcome> actions) {
    }

    private final TaskCommands tasks;
    private final UserDirectory users;
    private final Channels channels;

    RuleEngine(TaskCommands tasks, UserDirectory users, Channels channels) {
        this.tasks = tasks;
        this.users = users;
        this.channels = channels;
    }

    public TaskOutcome runOn(AutomationRule rule, String taskKey) {
        Optional<TaskFacts> facts = tasks.facts(taskKey);
        if (facts.isEmpty()) {
            return new TaskOutcome(taskKey, false, List.of());
        }
        return execute(rule, facts.get());
    }

    public List<TaskOutcome> runOnAll(AutomationRule rule) {
        List<TaskOutcome> outcomes = new ArrayList<>();
        for (TaskFacts facts : tasks.allFacts()) {
            TaskOutcome outcome = execute(rule, facts);
            if (outcome.matched()) {
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    public boolean matches(AutomationRule rule, TaskFacts facts) {
        return evaluate(rule.getConditions(), facts);
    }

    private TaskOutcome execute(AutomationRule rule, TaskFacts facts) {
        if (!evaluate(rule.getConditions(), facts)) {
            return new TaskOutcome(facts.taskKey(), false, List.of());
        }

        List<ActionOutcome> results = new ArrayList<>();
        ActorContext.runAs(rule.getName(), () -> {
            for (Map<String, Object> action : rule.getActions()) {
                results.add(apply(action, facts));
            }
        });

        return new TaskOutcome(facts.taskKey(), true, results);
    }

    private ActionOutcome apply(Map<String, Object> action, TaskFacts facts) {
        String kind = text(action.get("kind"));
        String value = text(action.get("value"));
        String key = facts.taskKey();

        try {
            switch (kind == null ? "" : kind) {
                case "setStatus" -> {
                    tasks.setStatus(key, strip(value, "status."));
                    return applied(kind, value);
                }
                case "setPriority" -> {
                    tasks.setPriority(key, strip(value, "priority."));
                    return applied(kind, value);
                }
                case "assign" -> {
                    UUID user = resolveUser(value);
                    if (user == null) {
                        return skipped(kind, value, "run.personNotFound");
                    }
                    tasks.assign(key, user);
                    return applied(kind, value);
                }
                case "assignReviewer" -> {
                    UUID user = resolveUser(value);
                    if (user == null) {
                        return skipped(kind, value, "run.personNotFound");
                    }
                    tasks.setReviewer(key, user);
                    return applied(kind, value);
                }
                case "addLabel" -> {
                    if (value == null || value.isBlank()) {
                        return skipped(kind, value, "run.valueMissing");
                    }
                    tasks.addLabel(key, value.trim());
                    return applied(kind, value);
                }
                case "setDueDate" -> {
                    Integer days = parseDayOffset(value);
                    if (days == null) {
                        return skipped(kind, value, "run.valueMissing");
                    }
                    tasks.setDueInDays(key, days);
                    return applied(kind, value);
                }
                case "notifyChannel" -> {
                    if (value == null || value.isBlank()) {
                        return skipped(kind, value, "run.valueMissing");
                    }
                    Channels.Delivery delivery = channels.notifyChannel(value.trim(), key, message(facts));
                    return delivery.ok()
                            ? applied(kind, value)
                            : skipped(kind, value, "run.channelFailed");
                }
                case "comment" -> {
                    if (value == null || value.isBlank()) {
                        return skipped(kind, value, "run.valueMissing");
                    }
                    tasks.addComment(key, value);
                    return applied(kind, value);
                }
                default -> {
                    return skipped(kind, value, "run.actionNotSupported");
                }
            }
        } catch (RuntimeException e) {
            return new ActionOutcome(kind, value, false, "run.actionFailed");
        }
    }

    private static String message(TaskFacts facts) {
        String rule = ActorContext.currentLabel();
        String base = facts.taskKey() + " (" + facts.statusCode() + ")";
        return rule == null || rule.isBlank() ? base : rule + ": " + base;
    }

    private static ActionOutcome applied(String kind, String value) {
        return new ActionOutcome(kind, value, true, "run.actionApplied");
    }

    private static ActionOutcome skipped(String kind, String value, String detailKey) {
        return new ActionOutcome(kind, value, false, detailKey);
    }

    private boolean evaluate(Map<String, Object> node, TaskFacts facts) {
        if (node == null || node.isEmpty()) {
            return true;
        }

        String kind = text(node.get("kind"));
        if ("group".equals(kind)) {
            List<?> children = node.get("children") instanceof List<?> list ? list : List.of();
            if (children.isEmpty()) {
                return true;
            }
            boolean all = !"or".equalsIgnoreCase(text(node.get("join")));
            for (Object child : children) {
                if (!(child instanceof Map<?, ?> map)) {
                    continue;
                }
                boolean result = evaluate(castNode(map), facts);
                if (all && !result) {
                    return false;
                }
                if (!all && result) {
                    return true;
                }
            }
            return all;
        }

        return condition(node, facts);
    }

    private boolean condition(Map<String, Object> node, TaskFacts facts) {
        String field = strip(text(node.get("fieldKey")), "cond.");
        String op = text(node.get("op"));
        List<String> values = new ArrayList<>();
        if (node.get("values") instanceof List<?> list) {
            list.forEach(entry -> values.add(text(entry)));
        }

        return switch (field == null ? "" : field) {
            case "priority" -> compareText(facts.priority(), op, values, "priority.");
            case "status" -> compareText(facts.statusCode(), op, values, "status.");
            case "label" -> compareLabels(facts.labels(), op, values);
            case "estimate" -> compareNumber(facts.estimate(), op, values);
            case "assignee" -> compareAssignee(facts.assigneeId(), op, values);
            case "dueIn" -> compareDueIn(facts.dueDate(), op, values);
            default -> true;
        };
    }

    private boolean compareText(String actual, String op, List<String> values, String prefix) {
        List<String> wanted = values.stream().map(value -> strip(value, prefix)).toList();
        String current = actual == null ? "" : actual;

        return switch (op == null ? "" : op) {
            case "is", "isOneOf" -> wanted.stream().anyMatch(value -> value.equalsIgnoreCase(current));
            case "isNot", "isNoneOf" -> wanted.stream().noneMatch(value -> value.equalsIgnoreCase(current));
            case "isEmpty" -> current.isBlank();
            case "isNotEmpty" -> !current.isBlank();
            default -> true;
        };
    }

    private boolean compareLabels(List<String> labels, String op, List<String> values) {
        return switch (op == null ? "" : op) {
            case "contains", "is", "isOneOf" ->
                values.stream().anyMatch(value -> labels.stream().anyMatch(label -> label.equalsIgnoreCase(value)));
            case "notContains", "isNot", "isNoneOf" ->
                values.stream().noneMatch(value -> labels.stream().anyMatch(label -> label.equalsIgnoreCase(value)));
            case "isEmpty" -> labels.isEmpty();
            case "isNotEmpty" -> !labels.isEmpty();
            default -> true;
        };
    }

    private boolean compareNumber(Integer actual, String op, List<String> values) {
        Integer wanted = firstNumber(values);
        if (wanted == null) {
            return switch (op == null ? "" : op) {
                case "isEmpty" -> actual == null;
                case "isNotEmpty" -> actual != null;
                default -> true;
            };
        }
        int current = actual == null ? 0 : actual;

        return switch (op == null ? "" : op) {
            case "greaterThan" -> current > wanted;
            case "lessThan" -> current < wanted;
            case "is", "isOneOf" -> current == wanted;
            case "isNot", "isNoneOf" -> current != wanted;
            case "isEmpty" -> actual == null;
            case "isNotEmpty" -> actual != null;
            default -> true;
        };
    }

    private boolean compareAssignee(UUID assigneeId, String op, List<String> values) {
        boolean present = assigneeId != null;
        return switch (op == null ? "" : op) {
            case "isEmpty" -> !present;
            case "isNotEmpty" -> present;
            case "is", "isOneOf" ->
                present && values.stream().anyMatch(value -> assigneeId.equals(resolveUser(value)));
            case "isNot", "isNoneOf" ->
                !present || values.stream().noneMatch(value -> assigneeId.equals(resolveUser(value)));
            default -> true;
        };
    }

    private boolean compareDueIn(LocalDate dueDate, String op, List<String> values) {
        if (dueDate == null) {
            return "isEmpty".equals(op);
        }
        Integer hours = parseHours(values.isEmpty() ? null : values.get(0));
        if (hours == null) {
            return "isNotEmpty".equals(op);
        }
        long hoursLeft = ChronoUnit.HOURS.between(
                LocalDate.now().atStartOfDay(), dueDate.atStartOfDay());

        return switch (op == null ? "" : op) {
            case "isBefore", "lessThan" -> hoursLeft <= hours;
            case "isAfter", "greaterThan" -> hoursLeft > hours;
            case "isNotEmpty" -> true;
            case "isEmpty" -> false;
            default -> true;
        };
    }

    UUID resolveUser(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String needle = value.trim();
        try {
            return UUID.fromString(needle);
        } catch (IllegalArgumentException ignored) {
            return resolveByName(needle);
        }

    }

    private UUID resolveByName(String needle) {
        String lowered = needle.toLowerCase(Locale.ROOT);
        return users.findAll().stream()
                .filter(user -> matchesUser(user, lowered))
                .map(UserView::id)
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesUser(UserView user, String needle) {
        return user.name().toLowerCase(Locale.ROOT).equals(needle)
                || user.shortName().toLowerCase(Locale.ROOT).equals(needle)
                || user.email().toLowerCase(Locale.ROOT).equals(needle)
                || user.initials().toLowerCase(Locale.ROOT).equals(needle);
    }

    static Integer parseDayOffset(String value) {
        if (value == null) {
            return null;
        }
        Integer number = firstNumber(List.of(value));
        if (number == null) {
            return null;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if (lowered.contains("robocz") || lowered.contains("business") || lowered.contains("work")) {
            return businessDaysToCalendarDays(number);
        }
        return number;
    }

    private static int businessDaysToCalendarDays(int businessDays) {
        LocalDate cursor = LocalDate.now();
        int added = 0;
        int calendar = 0;
        while (added < businessDays) {
            cursor = cursor.plusDays(1);
            calendar++;
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return calendar;
    }

    static Integer parseHours(String value) {
        if (value == null) {
            return null;
        }
        Integer number = firstNumber(List.of(value));
        if (number == null) {
            return null;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        if (lowered.contains("dzie") || lowered.contains("dni") || lowered.contains("day")) {
            return number * 24;
        }
        return number;
    }

    private static Integer firstNumber(List<String> values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            StringBuilder digits = new StringBuilder();
            boolean negative = value.trim().startsWith("-");
            for (char character : value.toCharArray()) {
                if (Character.isDigit(character)) {
                    digits.append(character);
                } else if (digits.length() > 0) {
                    break;
                }
            }
            if (digits.length() > 0) {
                int parsed = Integer.parseInt(digits.toString());
                return negative ? -parsed : parsed;
            }
        }
        return null;
    }

    private static String strip(String value, String prefix) {
        if (value == null) {
            return null;
        }
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castNode(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
