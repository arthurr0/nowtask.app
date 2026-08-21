package app.nowtask.integrations;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class Mailer {

    static final String LOGO_LIGHT = "logo-light";
    static final String LOGO_DARK = "logo-dark";

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final Resource LIGHT = new ClassPathResource("mail/logo-light.png");
    private static final Resource DARK = new ClassPathResource("mail/logo-dark.png");

    record Result(boolean sent, String detail) {
    }

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String fromName;
    private final String replyTo;

    Mailer(
            ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${nowtask.mail.from:nowtask@localhost}") String from,
            @Value("${nowtask.mail.from-name:nowtask}") String fromName,
            @Value("${nowtask.mail.reply-to:}") String replyTo) {
        this.sender = sender;
        this.host = host;
        this.from = from;
        this.fromName = fromName;
        this.replyTo = replyTo;
    }

    boolean configured() {
        return !host.isBlank() && sender.getIfAvailable() != null;
    }

    Result deliver(List<String> recipients, MailMessage message) {
        if (recipients == null || recipients.isEmpty()) {
            return new Result(false, "No recipient address");
        }
        if (!configured()) {
            return new Result(false, "Mail is not configured, set NOWTASK_MAIL_HOST");
        }

        JavaMailSender mail = sender.getObject();

        try {
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(sender());
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            helper.addInline(LOGO_LIGHT, LIGHT, "image/png");
            helper.addInline(LOGO_DARK, DARK, "image/png");

            if (!replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            mime.setHeader("Auto-Submitted", "auto-generated");
            mail.send(mime);

            return new Result(true, "Sent to " + String.join(", ", recipients));
        } catch (Exception e) {
            String reason = e.getMessage();
            return new Result(false, reason == null || reason.isBlank() ? e.getClass().getSimpleName() : reason);
        }
    }

    void send(String to, MailMessage message) {
        Result result = deliver(List.of(to), message);
        if (!result.sent()) {
            log.warn("The message \"{}\" to {} was not sent: {}", message.subject(), to, result.detail());
        }
    }

    private InternetAddress sender() throws UnsupportedEncodingException {
        return new InternetAddress(from, fromName, StandardCharsets.UTF_8.name());
    }
}
