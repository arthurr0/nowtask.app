package app.nowtask.shared;

import java.util.List;
import java.util.Optional;

public enum TaskField {
    DESCRIPTION("description"),
    PRIORITY("priority"),
    ASSIGNEE("assignee"),
    REVIEWER("reviewer"),
    DUE_DATE("dueDate"),
    ESTIMATE("estimate"),
    EPIC("epic"),
    LABELS("labels"),
    SPRINT("sprint");

    public static final String CUSTOM_PREFIX = "custom:";

    private final String key;

    TaskField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static List<String> keys() {
        return java.util.Arrays.stream(values()).map(TaskField::key).toList();
    }

    public static Optional<TaskField> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return java.util.Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
    }

    public static boolean isCustom(String key) {
        return key != null && key.startsWith(CUSTOM_PREFIX);
    }

    public static String customKey(String fieldKey) {
        return CUSTOM_PREFIX + fieldKey;
    }

    public static String customFieldKey(String key) {
        return isCustom(key) ? key.substring(CUSTOM_PREFIX.length()) : key;
    }
}
