package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import tools.jackson.databind.ObjectMapper;

class WebhookChannelTest {

    private static final String APP_URL = "https://nowtask.app";

    private final ObjectMapper json = new ObjectMapper();
    private final MessageSource messages = bundle();

    private static MessageSource bundle() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:mail/messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(MailConfig.DEFAULT_LOCALE);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

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

    private static final EventDetails FULL = new EventDetails(
            "Kopanie rudy w kopalni", "Artur Kołecki", "Wysoki", "30 sie 2026",
            List.of("bug", "backend"), "Artur Kołecki");

    @Test
    void sendsADiscordEmbedWithTheTaskAndItsFields() throws Exception {
        String body = capture(
                Map.of("format", "discord"), "taskStatusChanged", "MINING-1", "Reported → Cancelled", FULL);
        var embed = json.readTree(body).path("embeds").get(0);

        assertThat(embed.path("author").path("name").asString()).isEqualTo("Zmiana statusu");
        assertThat(embed.path("title").asString()).isEqualTo("MINING-1");
        assertThat(embed.path("url").asString()).isEqualTo(APP_URL + "/app/tasks/MINING-1");
        assertThat(embed.path("description").asString()).isEqualTo("Reported → Cancelled");
        assertThat(embed.path("color").asInt()).isEqualTo(0x55585F);
        assertThat(embed.path("footer").path("text").asString()).isEqualTo("nowtask · Artur Kołecki");
        assertThat(embed.path("footer").path("icon_url").asString())
                .isEqualTo(APP_URL + "/brand/favicon-32.png");
        assertThat(embed.path("timestamp").asString()).isNotBlank();

        var fields = embed.path("fields");
        assertThat(fields).hasSize(5);
        assertThat(fields.get(0).path("name").asString()).isEqualTo("Zadanie");
        assertThat(fields.get(0).path("value").asString()).isEqualTo("Kopanie rudy w kopalni");
        assertThat(fields.get(0).path("inline").asBoolean()).isFalse();
        assertThat(fields.get(1).path("name").asString()).isEqualTo("Przypisane");
        assertThat(fields.get(1).path("value").asString()).isEqualTo("Artur Kołecki");
        assertThat(fields.get(1).path("inline").asBoolean()).isTrue();
        assertThat(fields.get(2).path("name").asString()).isEqualTo("Priorytet");
        assertThat(fields.get(3).path("name").asString()).isEqualTo("Termin");
        assertThat(fields.get(4).path("name").asString()).isEqualTo("Etykiety");
        assertThat(fields.get(4).path("value").asString()).isEqualTo("bug, backend");
        assertThat(fields.get(4).path("inline").asBoolean()).isFalse();
    }

    @Test
    void leavesOutTheFieldsATaskDoesNotHave() throws Exception {
        EventDetails bare = new EventDetails("Kopanie rudy", null, null, null, List.of(), null);
        String body = capture(Map.of("format", "discord"), "taskCreated", "MINING-3", "Kopanie rudy", bare);
        var embed = json.readTree(body).path("embeds").get(0);

        assertThat(embed.path("fields")).hasSize(1);
        assertThat(embed.path("fields").get(0).path("name").asString()).isEqualTo("Zadanie");
        assertThat(embed.path("footer").path("text").asString()).isEqualTo("nowtask");
    }

    @Test
    void doesNotRepeatTheAssigneeInTheDescription() throws Exception {
        String body = capture(Map.of("format", "discord"), "taskAssigned", "MINING-1", "Artur Kołecki", FULL);
        var embed = json.readTree(body).path("embeds").get(0);

        assertThat(embed.path("description").isMissingNode()).isTrue();
        assertThat(embed.path("fields").get(1).path("value").asString()).isEqualTo("Artur Kołecki");
    }

    @Test
    void fallsBackToPlainDiscordContentWithoutATask() throws Exception {
        String body = capture(Map.of("format", "discord"), "test", null, "Test message from nowtask",
                EventDetails.of(null));

        assertThat(json.readTree(body).path("content").asString())
                .isEqualTo("Wysyłka próbna\nTest message from nowtask");
    }

    @Test
    void sendsSlackBlocksWithALinkedTaskAndFields() throws Exception {
        String body = capture(Map.of("format", "slack"), "taskCreated", "MINING-2", "Kopanie rudy", FULL);
        var block = json.readTree(body).path("blocks").get(0);

        assertThat(json.readTree(body).path("text").asString()).isEqualTo("Nowe zadanie: MINING-2");
        assertThat(block.path("text").path("text").asString())
                .isEqualTo("*Nowe zadanie* <" + APP_URL + "/app/tasks/MINING-2|MINING-2>\nKopanie rudy");
        assertThat(block.path("fields")).hasSize(5);
        assertThat(block.path("fields").get(0).path("text").asString())
                .isEqualTo("*Zadanie*\nKopanie rudy w kopalni");
        assertThat(block.path("fields").get(1).path("text").asString())
                .isEqualTo("*Przypisane*\nArtur Kołecki");
    }

    @Test
    void keepsTheInternalPayloadForAPlainWebhook() throws Exception {
        String body = capture(Map.of(), "taskAssigned", "NOW-12", "Przypisano", FULL);

        assertThat(json.readTree(body).path("event").asString()).isEqualTo("taskAssigned");
        assertThat(json.readTree(body).path("taskKey").asString()).isEqualTo("NOW-12");
        assertThat(json.readTree(body).path("message").asString()).isEqualTo("Przypisano");
    }

    private String capture(
            Map<String, Object> config, String event, String taskKey, String message, EventDetails details)
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
            var delivery = new WebhookChannel(json, messages, APP_URL)
                    .send(new Integration("webhook", "kanał", checked), event, taskKey, message, details);
            assertThat(delivery.ok()).isTrue();
            return received.get();
        } finally {
            server.stop(0);
        }
    }
}
