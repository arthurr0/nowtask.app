package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RoleId {
    ADMIN("admin"),
    MANAGER("manager"),
    MEMBER("member"),
    GUEST("guest");

    private final String code;

    RoleId(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static RoleId of(String code) {
        for (RoleId value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Nieznana rola: " + code);
    }

    public boolean seesProtectedFields() {
        return this == ADMIN || this == MANAGER;
    }
}
