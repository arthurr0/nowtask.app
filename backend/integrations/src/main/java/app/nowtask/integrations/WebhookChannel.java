package app.nowtask.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.api.Channels.Delivery;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class WebhookChannel {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String SIGNATURE_HEADER = "X-Nowtask-Signature";
    private static final String EVENT_HEADER = "X-Nowtask-Event";
    private static final int DISCORD_LIMIT = 2000;

    private static final Map<String, Integer> COLORS = Map.of(
            IntegrationService.EVENT_TASK_CREATED, 0x22C55E,
            IntegrationService.EVENT_TASK_STATUS_CHANGED, 0x3B82F6,
            IntegrationService.EVENT_TASK_ASSIGNED, 0xA855F7,
            IntegrationService.EVENT_RULE_NOTIFY, 0xF59E0B);
    private static final int NEUTRAL = 0x6B7280;

    private final HttpClient http;
    private final ObjectMapper json;
    private final MessageSource messages;
    private final String appUrl;

    WebhookChannel(
            ObjectMapper json,
            MessageSource messages,
            @Value("${nowtask.app.url:http://localhost:8080}") String appUrl) {
        this.json = json;
        this.messages = messages;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    Delivery send(
            Integration integration, String event, String taskKey, String message, EventDetails details) {
        String url = integration.text("url");
        if (url.isBlank()) {
            return new Delivery(false, "The webhook has no address");
        }

        URI target;
        try {
            target = URI.create(url);
        } catch (IllegalArgumentException e) {
            return new Delivery(false, "The webhook address is invalid: " + url);
        }

        String scheme = target.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return new Delivery(false, "A webhook accepts only http and https addresses");
        }

        String body = payload(WebhookFormats.resolve(integration.text("format"), target),
                integration, event, taskKey, message, details);

        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header(EVENT_HEADER, event)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        String secret = integration.text("secret");
        if (!secret.isBlank()) {
            request.header(SIGNATURE_HEADER, "sha256=" + sign(secret, body));
        }

        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new Delivery(ok, ok ? "HTTP " + response.statusCode() : detail(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Delivery(false, "Delivery interrupted");
        } catch (Exception e) {
            return new Delivery(false, reason(e));
        }
    }

    private String payload(
            WebhookFormats format,
            Integration integration,
            String event,
            String taskKey,
            String message,
            EventDetails details) {
        return switch (format) {
            case DISCORD -> write(discord(event, taskKey, message, details));
            case SLACK -> write(slack(event, taskKey, message, details));
            case GENERIC -> write(generic(integration, event, taskKey, message));
        };
    }

    private Map<String, Object> discord(String event, String taskKey, String message, EventDetails details) {
        String label = label(event);
        String body = trimmed(message == null ? "" : message.strip(), DISCORD_LIMIT);

        if (taskKey == null || taskKey.isBlank()) {
            return Map.of("content", trimmed(body.isBlank() ? label : label + "\n" + body, DISCORD_LIMIT));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("author", Map.of("name", label));
        embed.put("title", heading(taskKey, details));
        embed.put("url", link(taskKey));
        if (!body.isBlank() && !body.equals(details.assignee())) {
            embed.put("description", body);
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        discordField(fields, "assignee", details.assignee(), true);
        discordField(fields, "priority", details.priority(), true);
        discordField(fields, "due", details.due(), true);
        discordField(fields, "labels", join(details.labels()), false);
        if (!fields.isEmpty()) {
            embed.put("fields", fields);
        }

        embed.put("color", COLORS.getOrDefault(event, NEUTRAL));
        embed.put("footer", Map.of("text", footer(details)));
        embed.put("timestamp", Instant.now().toString());

        return Map.of("embeds", List.of(embed));
    }

    private Map<String, Object> slack(String event, String taskKey, String message, EventDetails details) {
        String label = label(event);
        String body = message == null ? "" : message.strip();

        if (taskKey == null || taskKey.isBlank()) {
            String text = body.isBlank() ? "*" + label + "*" : "*" + label + "*\n" + body;
            return Map.of("text", label, "blocks", List.of(section(text, List.of())));
        }

        StringBuilder headline = new StringBuilder("*").append(label).append("* <")
                .append(link(taskKey)).append("|").append(heading(taskKey, details)).append(">");
        if (!body.isBlank() && !body.equals(details.assignee())) {
            headline.append("\n").append(body);
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        slackField(fields, "assignee", details.assignee());
        slackField(fields, "priority", details.priority());
        slackField(fields, "due", details.due());
        slackField(fields, "labels", join(details.labels()));

        return Map.of(
                "text", label + ": " + taskKey,
                "blocks", List.of(section(headline.toString(), fields)));
    }

    private static Map<String, Object> section(String text, List<Map<String, Object>> fields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "section");
        block.put("text", Map.of("type", "mrkdwn", "text", text));
        if (!fields.isEmpty()) {
            block.put("fields", fields);
        }
        return block;
    }

    private void discordField(List<Map<String, Object>> fields, String key, String value, boolean inline) {
        if (value == null || value.isBlank()) {
            return;
        }
        fields.add(Map.of("name", field(key), "value", value, "inline", inline));
    }

    private void slackField(List<Map<String, Object>> fields, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        fields.add(Map.of("type", "mrkdwn", "text", "*" + field(key) + "*\n" + value));
    }

    private static String heading(String taskKey, EventDetails details) {
        String title = details.title();
        return title == null || title.isBlank() ? taskKey : taskKey + " · " + title;
    }

    private static String join(List<String> labels) {
        return labels == null || labels.isEmpty() ? null : String.join(", ", labels);
    }

    private static String footer(EventDetails details) {
        String actor = details.actor();
        return actor == null || actor.isBlank() ? "nowtask" : "nowtask · " + actor;
    }

    private String label(String event) {
        return resolve("mail.event." + event, event);
    }

    private String field(String key) {
        return resolve("mail.field." + key, key);
    }

    private String resolve(String code, String fallback) {
        String resolved = messages.getMessage(code, null, code, MailConfig.DEFAULT_LOCALE);
        return code.equals(resolved) ? fallback : resolved;
    }

    private String link(String taskKey) {
        return appUrl + "/app/tasks/" + taskKey;
    }

    private static Map<String, Object> generic(
            Integration integration, String event, String taskKey, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("at", Instant.now().toString());
        body.put("integration", integration.getName());
        body.put("taskKey", taskKey);
        body.put("message", message);
        return body;
    }

    private static String trimmed(String prefix, int limit) {
        return prefix.length() <= limit ? prefix : prefix.substring(0, limit);
    }

    private String write(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            return "";
        }
    }

    private static String detail(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().strip();
        if (body.isBlank()) {
            return "HTTP " + response.statusCode();
        }
        return "HTTP " + response.statusCode() + ": " + (body.length() > 300 ? body.substring(0, 300) : body);
    }

    private static String reason(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
