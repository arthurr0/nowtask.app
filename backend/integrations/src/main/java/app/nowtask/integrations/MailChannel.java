package app.nowtask.integrations;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.api.Channels.Delivery;

@Component
class MailChannel {

    private final Mailer mailer;
    private final MailRenderer renderer;

    MailChannel(Mailer mailer, MailRenderer renderer) {
        this.mailer = mailer;
        this.renderer = renderer;
    }

    Delivery send(Integration integration, String event, String taskKey, String message) {
        String to = integration.text("to");
        if (to.isBlank()) {
            return new Delivery(false, "The mail integration has no recipient address");
        }

        Locale locale = MailConfig.DEFAULT_LOCALE;
        String key = taskKey == null || taskKey.isBlank() ? null : taskKey;
        String title = label(event, locale);

        Map<String, Object> model = MailRenderer.model();
        model.put("title", title);
        model.put("event", event);
        model.put("message", message);
        model.put("taskKey", key);
        model.put("link", key == null ? null : renderer.appUrl() + "/app/tasks/" + key);
        model.put("preheader", renderer.message("mail.notification.preheader", locale));

        String subject = key == null
                ? renderer.message("mail.notification.subject", locale, title)
                : renderer.message("mail.notification.subjectTask", locale, title, key);

        List<String> recipients = Arrays.stream(to.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(address -> !address.isBlank())
                .toList();

        Mailer.Result result = mailer.deliver(recipients, renderer.render("notification", locale, subject, model));

        return new Delivery(result.sent(), result.detail());
    }

    private String label(String event, Locale locale) {
        String code = "mail.event." + event;
        String resolved = renderer.message(code, locale);
        return code.equals(resolved) ? event : resolved;
    }
}
