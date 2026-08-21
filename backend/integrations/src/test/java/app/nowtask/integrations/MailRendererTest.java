package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;

class MailRendererTest {

    private static final String APP_URL = "https://nowtask.app";
    private static final Path PREVIEW = Path.of("build", "mail-preview");
    private static final Instant EXPIRES = Instant.parse("2026-09-04T10:15:30Z");
    private static final List<Locale> LOCALES = List.of(
            Locale.forLanguageTag("pl"), Locale.ENGLISH, Locale.GERMAN);

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThymeleafAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues("spring.thymeleaf.template-resolver-order=2");

    @Test
    void everyTemplateRendersInEveryLanguage() {
        context.run(loaded -> {
            MailRenderer renderer = renderer(loaded);

            Files.createDirectories(PREVIEW);

            for (Locale locale : LOCALES) {
                for (Map.Entry<String, Map<String, Object>> template : templates(locale).entrySet()) {
                    String name = template.getKey();
                    String subject = renderer.message("mail." + subjectKey(name) + ".subject", locale, "Marta",
                            "Studio Kolektyw");

                    MailMessage message = renderer.render(name, locale, subject, template.getValue());

                    assertThat(message.html())
                            .describedAs("%s in %s", name, locale)
                            .contains("cid:logo-light", "cid:logo-dark", APP_URL)
                            .doesNotContain("mail.", "??");
                    assertThat(message.text())
                            .describedAs("%s in %s", name, locale)
                            .isNotBlank()
                            .doesNotContain("mail.", "[#");

                    write(name + "_" + locale.getLanguage(), message);
                }
            }
        });
    }

    @Test
    void theReminderCopyReplacesTheRegularOne() {
        context.run(loaded -> {
            MailRenderer renderer = renderer(loaded);
            Locale locale = Locale.forLanguageTag("pl");

            Map<String, Object> model = templates(locale).get("invite");
            model.put("reminder", true);

            MailMessage reminder = renderer.render("invite", locale, "x", model);

            assertThat(reminder.html())
                    .contains("przypomnienie", "wciąż czeka")
                    .doesNotContain("Przyjmij zaproszenie");
            assertThat(reminder.text())
                    .contains("wciąż czeka")
                    .doesNotContain("Przyjmij zaproszenie");
        });
    }

    @Test
    void aNotificationWithoutATaskHasNoLink() {
        context.run(loaded -> {
            MailRenderer renderer = renderer(loaded);
            Locale locale = Locale.forLanguageTag("pl");

            Map<String, Object> model = templates(locale).get("notification");
            model.put("taskKey", null);
            model.put("link", null);

            MailMessage message = renderer.render("notification", locale, "x", model);

            assertThat(message.html()).doesNotContain("Otwórz zadanie", "/app/tasks/");
            assertThat(message.text()).doesNotContain("Otwórz zadanie", "/app/tasks/");
        });
    }

    private static MailRenderer renderer(org.springframework.context.ApplicationContext loaded) {
        return new MailRenderer(
                loaded.getBean(SpringTemplateEngine.class), loaded.getBean(MessageSource.class), APP_URL);
    }

    private static Map<String, Map<String, Object>> templates(Locale locale) {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();

        all.put("verify-email", model(Map.of(
                "name", "Marta",
                "link", APP_URL + "/verify-email?token=abc",
                "expires", MailRenderer.date(EXPIRES, locale),
                "preheader", "Jeden klik.")));

        all.put("invite", model(Map.of(
                "organizationName", "Studio Kolektyw",
                "inviterName", "Marta",
                "roleName", "Manager",
                "reminder", Boolean.FALSE,
                "link", APP_URL + "/invite/abc",
                "expires", MailRenderer.date(EXPIRES, locale),
                "preheader", "Zaproszenie.")));

        all.put("welcome", model(Map.of(
                "name", "Marta",
                "link", APP_URL + "/app/board",
                "preheader", "Start.")));

        all.put("member-joined", model(Map.of(
                "memberName", "Piotr Nowak",
                "memberEmail", "piotr@nowtask.app",
                "organizationName", "Studio Kolektyw",
                "link", APP_URL + "/app/admin",
                "preheader", "Zespół.")));

        all.put("password-reset", model(Map.of(
                "name", "Marta",
                "link", APP_URL + "/reset-password?token=abc",
                "expires", MailRenderer.dateTime(EXPIRES, locale),
                "preheader", "Reset.")));

        all.put("password-changed", model(Map.of(
                "name", "Marta",
                "changedAt", MailRenderer.dateTime(EXPIRES, locale),
                "link", APP_URL + "/login",
                "preheader", "Zmiana.")));

        all.put("notification", model(Map.of(
                "title", "Przypisanie zadania",
                "event", "taskAssigned",
                "message", "NT-142: task assigned",
                "taskKey", "NT-142",
                "link", APP_URL + "/app/tasks/NT-142",
                "preheader", "Powiadomienie.")));

        return all;
    }

    private static Map<String, Object> model(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.putAll(values);
        return copy;
    }

    private static String subjectKey(String template) {
        StringBuilder key = new StringBuilder();
        boolean upper = false;

        for (char letter : template.toCharArray()) {
            if (letter == '-') {
                upper = true;
                continue;
            }
            key.append(upper ? Character.toUpperCase(letter) : letter);
            upper = false;
        }

        return key.toString();
    }

    private static void write(String name, MailMessage message) throws IOException {
        Files.writeString(PREVIEW.resolve(name + ".html"), message.html(), StandardCharsets.UTF_8);
        Files.writeString(PREVIEW.resolve(name + ".txt"), message.text(), StandardCharsets.UTF_8);
    }
}
