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

    void send(String email, String kind, Map<String, Object> params, String taskKey, String taskTitle) {
        if (email == null || email.isBlank()) {
            return;
        }

        Locale locale = MailConfig.DEFAULT_LOCALE;
        Object[] args = params == null ? new Object[0] : params.values().toArray();
        String message = renderer.message("mail.notify." + kind, locale, args);
        String title = renderer.message("mail.notify.title", locale);
        boolean hasTask = taskKey != null && !taskKey.isBlank();
        String task = hasTask && taskTitle != null && !taskTitle.isBlank() ? taskTitle : null;

        Map<String, Object> model = MailRenderer.model();
        model.put("title", title);
        model.put("event", kind);
        model.put("message", message);
        model.put("taskKey", taskKey);
        model.put("taskTitle", task);
        model.put("link", hasTask
                ? renderer.appUrl() + "/app/tasks/" + taskKey
                : renderer.appUrl() + "/app/board");
        model.put("preheader", renderer.message("mail.notification.preheader", locale));

        String subject = MailSubjects.notification(renderer, locale, title, taskKey, task);

        executor.execute(() -> mailer.send(email, renderer.render("notification", locale, subject, model)));
    }
}
