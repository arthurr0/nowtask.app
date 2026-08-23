package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookChannelTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void picksDiscordFromTheAddressWhenNoFormatIsConfigured() {
        assertThat(WebhookFormats.resolve("", URI.create("https://discord.com/api/webhooks/1/token")))
                .isEqualTo(WebhookFormats.DISCORD);
        assertThat(WebhookFormats.resolve(null, URI.create("https://discordapp.com/api/webhooks/1/token")))
                .isEqualTo(WebhookFormats.DISCORD);
        assertThat(WebhookFormats.resolve("", URI.create("https://hooks.slack.com/services/T/B/X")))
                .isEqualTo(WebhookFormats.SLACK);
        assertThat(WebhookFormats.resolve("", URI.create("https://example.com/hook")))
                .isEqualTo(WebhookFormats.GENERIC);
        assertThat(WebhookFormats.resolve("generic", URI.create("https://discord.com/api/webhooks/1/token")))
                .isEqualTo(WebhookFormats.GENERIC);
        assertThat(WebhookFormats.resolve("", URI.create("https://notdiscord.com/hook")))
                .isEqualTo(WebhookFormats.GENERIC);
    }

    @Test
    void sendsDiscordContentInsteadOfTheInternalPayload() throws Exception {
        String body = capture(Map.of("format", "discord"), "taskCreated", "NOW-12", "Zadanie trafiło na tablicę");

        assertThat(json.readTree(body).path("content").asString())
                .isEqualTo("**taskCreated** · NOW-12\nZadanie trafiło na tablicę");
    }

    @Test
    void sendsSlackText() throws Exception {
        String body = capture(Map.of("format", "slack"), "test", null, "Test message from nowtask");

        assertThat(json.readTree(body).path("text").asString()).isEqualTo("*test*\nTest message from nowtask");
    }

    @Test
    void keepsTheInternalPayloadForAPlainWebhook() throws Exception {
        String body = capture(Map.of(), "taskAssigned", "NOW-12", "Przypisano");

        assertThat(json.readTree(body).path("event").asString()).isEqualTo("taskAssigned");
        assertThat(json.readTree(body).path("taskKey").asString()).isEqualTo("NOW-12");
        assertThat(json.readTree(body).path("message").asString()).isEqualTo("Przypisano");
    }

    private String capture(Map<String, Object> config, String event, String taskKey, String message)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> received = new AtomicReference<>("");
        server.createContext("/hook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            var checked = new java.util.LinkedHashMap<String, Object>(config);
            checked.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
            var delivery = new WebhookChannel(json)
                    .send(new Integration("webhook", "kanał", checked), event, taskKey, message);
            assertThat(delivery.ok()).isTrue();
            return received.get();
        } finally {
            server.stop(0);
        }
    }
}
