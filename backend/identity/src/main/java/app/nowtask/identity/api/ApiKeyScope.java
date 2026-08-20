package app.nowtask.identity.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum ApiKeyScope {
    TASKS_READ("tasks:read"),
    TASKS_WRITE("tasks:write"),
    TASKS_DELETE("tasks:delete"),
    RULES_READ("rules:read"),
    RULES_RUN("rules:run"),
    RULES_WRITE("rules:write"),
    METRICS_READ("metrics:read"),
    WORKSPACE_READ("workspace:read");

    public static final Set<ApiKeyScope> DEFAULT_GRANT =
            Set.of(TASKS_READ, WORKSPACE_READ, RULES_READ, METRICS_READ);

    private final String code;

    ApiKeyScope(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ApiKeyScope of(String code) {
        for (ApiKeyScope scope : values()) {
            if (scope.code.equalsIgnoreCase(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Nieznany zakres klucza API: " + code);
    }

    public static Set<ApiKeyScope> parse(String stored) {
        Set<ApiKeyScope> scopes = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return scopes;
        }
        for (String part : stored.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                scopes.add(of(trimmed));
            }
        }
        return scopes;
    }

    public static String join(Set<ApiKeyScope> scopes) {
        return scopes.stream().map(ApiKeyScope::code).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    public static List<ApiKeyScope> ordered(Set<ApiKeyScope> scopes) {
        return List.of(values()).stream().filter(scopes::contains).toList();
    }
}
