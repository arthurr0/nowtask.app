package app.nowtask.integrations;

import java.util.Locale;

final class MailSubjects {

    private MailSubjects() {
    }

    static String notification(MailRenderer renderer, Locale locale, String title, String taskKey, String taskTitle) {
        if (taskKey == null || taskKey.isBlank()) {
            return renderer.message("mail.notification.subject", locale, title);
        }
        if (taskTitle == null || taskTitle.isBlank()) {
            return renderer.message("mail.notification.subjectTask", locale, title, taskKey);
        }
        return renderer.message("mail.notification.subjectTaskTitle", locale, title, taskKey, taskTitle);
    }
}
