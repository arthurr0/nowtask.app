package app.nowtask.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    private final HttpClient http;
    private final ObjectMapper json;

    WebhookChannel(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    Delivery send(Integration integration, String event, String taskKey, String message) {
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
                integration, event, taskKey, message);

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
            WebhookFormats format, Integration integration, String event, String taskKey, String message) {
        return switch (format) {
            case DISCORD -> write(Map.of("content",
                    trimmed(text(event, taskKey, "**", "**") + line(message), DISCORD_LIMIT)));
            case SLACK -> write(Map.of("text", text(event, taskKey, "*", "*") + line(message)));
            case GENERIC -> write(generic(integration, event, taskKey, message));
        };
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

    private static String text(String event, String taskKey, String open, String close) {
        StringBuilder content = new StringBuilder(open).append(event).append(close);
        if (taskKey != null && !taskKey.isBlank()) {
            content.append(" · ").append(taskKey);
        }
        return content.toString();
    }

    private static String line(String message) {
        return message == null || message.isBlank() ? "" : "\n" + message;
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
