package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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

    @Test
    void sendsADiscordEmbedThatLinksTheTask() throws Exception {
        String body = capture(Map.of("format", "discord"), "taskStatusChanged", "MINING-1", "Nowe → Anulowane");
        var embed = json.readTree(body).path("embeds").get(0);

        assertThat(embed.path("author").path("name").asString()).isEqualTo("Zmiana statusu");
        assertThat(embed.path("title").asString()).isEqualTo("MINING-1");
        assertThat(embed.path("url").asString()).isEqualTo(APP_URL + "/app/tasks/MINING-1");
        assertThat(embed.path("description").asString()).isEqualTo("Nowe → Anulowane");
        assertThat(embed.path("color").asInt()).isEqualTo(0x3B82F6);
        assertThat(embed.path("timestamp").asString()).isNotBlank();
        assertThat(json.readTree(body).path("content").isMissingNode()).isTrue();
    }

    @Test
    void fallsBackToPlainDiscordContentWithoutATask() throws Exception {
        String body = capture(Map.of("format", "discord"), "test", null, "Test message from nowtask");

        assertThat(json.readTree(body).path("content").asString())
                .isEqualTo("Wysyłka próbna\nTest message from nowtask");
    }

    @Test
    void sendsSlackBlocksWithALinkedTask() throws Exception {
        String body = capture(Map.of("format", "slack"), "taskCreated", "MINING-2", "Kopanie rudy");
        var tree = json.readTree(body);

        assertThat(tree.path("text").asString()).isEqualTo("Nowe zadanie: MINING-2");
        assertThat(tree.path("blocks").get(0).path("text").path("text").asString())
                .isEqualTo("*Nowe zadanie* <" + APP_URL + "/app/tasks/MINING-2|MINING-2>\nKopanie rudy");
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
            var delivery = new WebhookChannel(json, messages, APP_URL)
                    .send(new Integration("webhook", "kanał", checked), event, taskKey, message);
            assertThat(delivery.ok()).isTrue();
            return received.get();
        } finally {
            server.stop(0);
        }
    }
}
