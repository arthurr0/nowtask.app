package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum TaskView {
    BOARD("board"),
    LIST("list"),
    TIMELINE("timeline"),
    CALENDAR("calendar");

    private final String code;

    TaskView(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TaskView::code).toList();
    }

    public static Optional<TaskView> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(view -> view.code.equals(code)).findFirst();
    }

    @JsonCreator
    public static TaskView of(String code) {
        return byCode(code).orElseThrow(() -> new IllegalArgumentException("Unknown task view " + code));
    }
}
