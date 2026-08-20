package app.nowtask.identity.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

public enum NavItemCode {
    OVERVIEW("overview"),
    MY_TASKS("my-tasks"),
    BOARD("board"),
    LIST("list"),
    TIMELINE("timeline"),
    AUTOMATIONS("automations"),
    AGENTS("agents"),
    REPORTS("reports");

    private final String code;

    NavItemCode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static NavItemCode of(String code) {
        for (NavItemCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Nieznana pozycja nawigacji: " + code);
    }

    public static List<NavItemCode> defaultOrder() {
        return List.of(values());
    }
}
