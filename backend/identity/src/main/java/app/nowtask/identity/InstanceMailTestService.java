package app.nowtask.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.InstanceMail;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.ServiceUnavailableException;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class InstanceMailTestService {

    private final InstanceMail mail;
    private final UserDirectory directory;
    private final InstanceAuditService audit;

    InstanceMailTestService(InstanceMail mail, UserDirectory directory, InstanceAuditService audit) {
        this.mail = mail;
        this.directory = directory;
        this.audit = audit;
    }

    public void sendTest(UUID actorId) {
        if (!mail.configured()) {
            throw new ServiceUnavailableException(
                    "Mail is not configured, set NOWTASK_MAIL_HOST", "MAIL_NOT_CONFIGURED");
        }

        String recipient = directory.currentUser().email();
        InstanceMail.Result result = mail.sendTest(recipient);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sent", result.sent());
        detail.put("detail", result.detail());

        audit.record(actorId, "mail.test", "mail", null, recipient, detail);

        if (!result.sent()) {
            throw new ServiceUnavailableException(
                    "The test message was not sent: " + result.detail(), "MAIL_SEND_FAILED");
        }
    }
}
