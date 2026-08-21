package app.nowtask.integrations;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
class MailRenderer {

    private static final Map<String, Locale> SUPPORTED = Map.of(
            "pl", Locale.forLanguageTag("pl"),
            "en", Locale.ENGLISH,
            "de", Locale.GERMAN);

    private final SpringTemplateEngine engine;
    private final MessageSource messages;
    private final String appUrl;

    MailRenderer(
            SpringTemplateEngine engine,
            MessageSource messages,
            @Value("${nowtask.app.url:http://localhost:8080}") String appUrl) {
        this.engine = engine;
        this.messages = messages;
        this.appUrl = trimSlash(appUrl);
    }

    MailMessage render(String template, Locale locale, String subject, Map<String, Object> model) {
        Context context = new Context(locale);
        context.setVariables(model == null ? Map.of() : model);
        context.setVariable("subject", subject);
        context.setVariable("appUrl", appUrl);

        String html = engine.process("mail/html/" + template, context);
        String text = engine.process("mail/text/" + template, context);

        return new MailMessage(subject, html, text.strip() + System.lineSeparator());
    }

    String message(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    String appUrl() {
        return appUrl;
    }

    static Locale locale(String language) {
        if (language == null || language.isBlank()) {
            return MailConfig.DEFAULT_LOCALE;
        }
        return SUPPORTED.getOrDefault(language.toLowerCase(Locale.ROOT).substring(0, 2), MailConfig.DEFAULT_LOCALE);
    }

    static String date(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    static String dateTime(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    static Map<String, Object> model() {
        return new HashMap<>();
    }

    private static String trimSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "http://localhost:8080" : value.trim();
        return cleaned.endsWith("/") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }
}
