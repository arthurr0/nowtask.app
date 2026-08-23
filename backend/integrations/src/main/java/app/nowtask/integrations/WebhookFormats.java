package app.nowtask.integrations;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

enum WebhookFormats {

    GENERIC,
    DISCORD,
    SLACK;

    static final Set<String> NAMES = Set.of("generic", "discord", "slack");

    static WebhookFormats resolve(String value, URI target) {
        return value == null || value.isBlank() ? byHost(target.getHost()) : of(value);
    }

    private static WebhookFormats byHost(String host) {
        if (host == null) {
            return GENERIC;
        }
        String name = host.toLowerCase(Locale.ROOT);
        if (matches(name, "discord.com") || matches(name, "discordapp.com")) {
            return DISCORD;
        }
        return matches(name, "slack.com") ? SLACK : GENERIC;
    }

    private static boolean matches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

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
