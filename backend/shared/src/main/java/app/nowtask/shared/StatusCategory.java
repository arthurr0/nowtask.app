package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusCategory {
    NOT_STARTED("notStarted"),
    IN_FLIGHT("inFlight"),
    DONE("done");

    private final String code;

    StatusCategory(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static StatusCategory of(String code) {
        for (StatusCategory value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Nieznana kategoria statusu: " + code);
    }
}
