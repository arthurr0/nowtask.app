package app.nowtask.integrations;

import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
class NotificationMailer {

    private final Mailer mailer;
    private final MailRenderer renderer;
    private final AsyncTaskExecutor executor;

    NotificationMailer(
            Mailer mailer,
            MailRenderer renderer,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.mailer = mailer;
        this.renderer = renderer;
        this.executor = executor;
    }

    void send(String email, String kind, Map<String, Object> params, String taskKey) {
        if (email == null || email.isBlank()) {
            return;
        }

        Locale locale = MailConfig.DEFAULT_LOCALE;
        Object[] args = params == null ? new Object[0] : params.values().toArray();
        String message = renderer.message("mail.notify." + kind, locale, args);
        String title = renderer.message("mail.notify.title", locale);

        Map<String, Object> model = MailRenderer.model();
        model.put("title", title);
        model.put("event", kind);
        model.put("message", message);
        model.put("taskKey", taskKey);
        model.put("link", taskKey == null || taskKey.isBlank()
                ? renderer.appUrl() + "/app/board"
                : renderer.appUrl() + "/app/tasks/" + taskKey);
        model.put("preheader", renderer.message("mail.notification.preheader", locale));

        String subject = taskKey == null || taskKey.isBlank()
                ? renderer.message("mail.notification.subject", locale, title)
                : renderer.message("mail.notification.subjectTask", locale, title, taskKey);

        executor.execute(() -> mailer.send(email, renderer.render("notification", locale, subject, model)));
    }
}
