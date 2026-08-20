package app.nowtask.integrations;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import app.nowtask.integrations.api.Channels.Delivery;

@Component
class MailChannel {

    private final ObjectProvider<JavaMailSender> mail;
    private final String host;
    private final String from;

    MailChannel(
            ObjectProvider<JavaMailSender> mail,
            @Value("${spring.mail.host:}") String host,
            @Value("${nowtask.mail.from:nowtask@localhost}") String from) {
        this.mail = mail;
        this.host = host;
        this.from = from;
    }

    Delivery send(Integration integration, String event, String taskKey, String message) {
        String to = integration.text("to");
        if (to.isBlank()) {
            return new Delivery(false, "The mail integration has no recipient address");
        }
        if (host.isBlank()) {
            return new Delivery(false, "Mail is not configured, set NOWTASK_MAIL_HOST");
        }

        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            return new Delivery(false, "Mail is not configured, set NOWTASK_MAIL_HOST");
        }

        SimpleMailMessage letter = new SimpleMailMessage();
        letter.setFrom(from);
        letter.setTo(to.split("\\s*,\\s*"));
        letter.setSubject(subject(event, taskKey));
        letter.setText(message);

        try {
            sender.send(letter);
            return new Delivery(true, "Sent to " + to);
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            return new Delivery(false, reason == null || reason.isBlank() ? e.getClass().getSimpleName() : reason);
        }
    }

    private static String subject(String event, String taskKey) {
        return taskKey == null || taskKey.isBlank()
                ? "nowtask: " + event
                : "nowtask: " + event + " (" + taskKey + ")";
    }
}
