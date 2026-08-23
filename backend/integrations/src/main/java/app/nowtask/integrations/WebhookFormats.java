package app.nowtask.integrations;

import java.util.Locale;
import java.util.Set;

enum WebhookFormats {

    GENERIC,
    DISCORD,
    SLACK;

    static final Set<String> NAMES = Set.of("generic", "discord", "slack");

    static WebhookFormats of(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "discord" -> DISCORD;
            case "slack" -> SLACK;
            default -> GENERIC;
        };
    }
}
