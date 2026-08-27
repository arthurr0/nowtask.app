package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum TaskOpenMode {
    DIALOG("dialog"),
    PAGE("page");

    private final String code;

    TaskOpenMode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TaskOpenMode::code).toList();
    }

    public static Optional<TaskOpenMode> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(mode -> mode.code.equals(code)).findFirst();
    }

    @JsonCreator
    public static TaskOpenMode of(String code) {
        return byCode(code).orElseThrow(() -> new IllegalArgumentException("Unknown task open mode " + code));
    }
}
