package app.nowtask.integrations;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.events.IdentityEvents;

@Component
class AccountMailListener {

    private final Mailer mailer;
    private final MailRenderer renderer;
    private final NotificationService notifications;
    private final AsyncTaskExecutor executor;

    AccountMailListener(
            Mailer mailer,
            MailRenderer renderer,
            NotificationService notifications,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.mailer = mailer;
        this.renderer = renderer;
        this.notifications = notifications;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onVerificationRequested(IdentityEvents.EmailVerificationRequested event) {
        Locale locale = MailRenderer.locale(event.locale());

        Map<String, Object> model = MailRenderer.model();
        model.put("name", event.name());
        model.put("link", link("/verify-email?token=" + encode(event.token())));
        model.put("expires", MailRenderer.date(event.expiresAt(), locale));
        model.put("preheader", renderer.message("mail.verifyEmail.preheader", locale));

        send(event.email(), "verify-email", locale, renderer.message("mail.verifyEmail.subject", locale), model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onInviteIssued(IdentityEvents.InviteIssued event) {
        Locale locale = MailRenderer.locale(event.locale());
        String subjectKey = event.reminder() ? "mail.invite.subjectReminder" : "mail.invite.subject";

        Map<String, Object> model = MailRenderer.model();
        model.put("organizationName", event.organizationName());
        model.put("inviterName", event.inviterName());
        model.put("roleName", event.roleName());
        model.put("reminder", event.reminder());
        model.put("link", link("/invite/" + encode(event.token())));
        model.put("expires", MailRenderer.date(event.expiresAt(), locale));
        model.put("preheader", renderer.message("mail.invite.preheader", locale));

        String subject = renderer.message(subjectKey, locale, event.inviterName(), event.organizationName());

        send(event.email(), "invite", locale, subject, model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onEmailVerified(IdentityEvents.EmailVerified event) {
        Locale locale = MailRenderer.locale(event.locale());

        Map<String, Object> model = MailRenderer.model();
        model.put("name", event.name());
        model.put("link", link("/app/board"));
        model.put("preheader", renderer.message("mail.welcome.preheader", locale));

        send(event.email(), "welcome", locale, renderer.message("mail.welcome.subject", locale), model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onMemberJoined(IdentityEvents.MemberJoined event) {
        if (event.inviterEmail() == null || event.inviterEmail().isBlank()) {
            return;
        }

        Locale locale = MailRenderer.locale(event.locale());

        Map<String, Object> model = MailRenderer.model();
        model.put("memberName", event.name());
        model.put("memberEmail", event.email());
        model.put("organizationName", event.organizationName());
        model.put("link", link("/app/admin"));
        model.put("preheader", renderer.message("mail.memberJoined.preheader", locale));

        String subject = renderer.message("mail.memberJoined.subject", locale, event.name());

        send(event.inviterEmail(), "member-joined", locale, subject, model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onPasswordResetRequested(IdentityEvents.PasswordResetRequested event) {
        Locale locale = MailRenderer.locale(event.locale());

        Map<String, Object> model = MailRenderer.model();
        model.put("name", event.name());
        model.put("link", link("/reset-password?token=" + encode(event.token())));
        model.put("expires", expiry(event.expiresAt(), locale));
        model.put("preheader", renderer.message("mail.passwordReset.preheader", locale));

        send(event.email(), "password-reset", locale, renderer.message("mail.passwordReset.subject", locale), model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onPasswordChanged(IdentityEvents.PasswordChanged event) {
        Locale locale = MailRenderer.locale(event.locale());

        Map<String, Object> model = MailRenderer.model();
        model.put("name", event.name());
        model.put("changedAt", expiry(event.at(), locale));
        model.put("link", link("/login"));
        model.put("preheader", renderer.message("mail.passwordChanged.preheader", locale));

        send(event.email(), "password-changed", locale,
                renderer.message("mail.passwordChanged.subject", locale), model);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onRenewalRequested(IdentityEvents.InviteRenewalRequested event) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("email", event.email());

        OrganizationContext scope = new OrganizationContext(
                event.inviterId(), event.organizationId(), null, null, Set.of());

        executor.execute(() -> OrganizationContextHolder.runAs(scope, () -> notifications.create(
                event.inviterId(), "inviteRenewal", "notify.inviteRenewal", params, null)));
    }

    private void send(String to, String template, Locale locale, String subject, Map<String, Object> model) {
        if (to == null || to.isBlank()) {
            return;
        }

        executor.execute(() -> mailer.send(to, renderer.render(template, locale, subject, model)));
    }

    private String link(String path) {
        return renderer.appUrl() + path;
    }

    private static String expiry(Instant instant, Locale locale) {
        return instant == null ? "" : MailRenderer.dateTime(instant, locale);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
