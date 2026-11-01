package app.nowtask.integrations;

import java.util.List;
import org.springframework.stereotype.Component;
import app.nowtask.identity.api.InstanceMail;

@Component
class InstanceMailService implements InstanceMail {

    private static final String SUBJECT = "nowtask test message";
    private static final String TEXT = "This is a test message from your nowtask instance."
            + " If you can read it, outgoing mail works.";
    private static final String HTML = "<p>This is a test message from your nowtask instance.</p>"
            + "<p>If you can read it, outgoing mail works.</p>";

    private final Mailer mailer;

    InstanceMailService(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public boolean configured() {
        return mailer.configured();
    }

    @Override
    public Result sendTest(String to) {
        Mailer.Result result = mailer.deliver(List.of(to), new MailMessage(SUBJECT, HTML, TEXT));
        return new Result(result.sent(), result.detail());
    }
}
