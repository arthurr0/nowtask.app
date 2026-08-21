package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.TooManyRequestsException;
import app.nowtask.shared.events.IdentityEvents;

@Service
@Transactional
public class EmailVerificationService {

    static final Duration VALIDITY = Duration.ofDays(7);
    private static final int RESEND_LIMIT_PER_HOUR = 3;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    EmailVerificationService(
            JdbcClient jdbc, AppUserRepository users, AuditLog audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.users = users;
        this.audit = audit;
        this.events = events;
    }

    public void request(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        if (isVerified(userId)) {
            return;
        }

        issue(user);
    }

    public void resend(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        if (isVerified(userId)) {
            throw new RuleViolationException("The address is already confirmed", "EMAIL_ALREADY_VERIFIED");
        }

        int lastHour = jdbc.sql("""
                        SELECT count(*) FROM email_verification
                        WHERE user_id = ? AND created_at >= now() - INTERVAL '1 hour'
                        """)
                .param(userId)
                .query(Integer.class)
                .single();

        if (lastHour >= RESEND_LIMIT_PER_HOUR) {
            throw new TooManyRequestsException("Too many verification mails, try again in an hour", 3600);
        }

        issue(user);
    }

    public UserView confirm(String token) {
        if (token == null || token.isBlank()) {
            throw new RuleViolationException("The link is invalid", "TOKEN_INVALID");
        }

        Row row = jdbc.sql("""
                        SELECT id, user_id, email, expires_at, confirmed_at
                        FROM email_verification WHERE token_hash = ?
                        """)
                .param(SecureTokens.hash(token.trim()))
                .query((rs, rowNum) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("email"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant()))
                .optional()
                .orElseThrow(() -> new RuleViolationException("The link is invalid", "TOKEN_INVALID"));

        if (row.confirmedAt() != null) {
            throw new RuleViolationException("The link has already been used", "TOKEN_USED");
        }
        if (row.expiresAt().isBefore(Instant.now())) {
            throw new RuleViolationException("The link has expired", "TOKEN_EXPIRED");
        }

        jdbc.sql("UPDATE email_verification SET confirmed_at = now() WHERE id = ?").param(row.id()).update();
        markVerified(row.userId());
        audit.record("account.emailVerified", row.email(), Map.of());

        AppUser user = users.findById(row.userId())
                .orElseThrow(() -> NotFoundException.of("User", row.userId()));

        events.publishEvent(new IdentityEvents.EmailVerified(
                user.getId(), user.getName(), user.getEmail(), LocaleContextHolder.getLocale().getLanguage()));

        return UserDirectoryService.toView(user, null);
    }

    public void markVerified(UUID userId) {
        jdbc.sql("UPDATE app_user SET email_verified_at = now() WHERE id = ? AND email_verified_at IS NULL")
                .param(userId)
                .update();
    }

    @Transactional(readOnly = true)
    public boolean isVerified(UUID userId) {
        return jdbc.sql("SELECT email_verified_at IS NOT NULL FROM app_user WHERE id = ?")
                .param(userId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    private void issue(AppUser user) {
        String token = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(VALIDITY);

        jdbc.sql("""
                        INSERT INTO email_verification (id, user_id, email, token_hash, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID(), user.getId(), user.getEmail(), SecureTokens.hash(token),
                        Timestamp.from(expiresAt))
                .update();

        events.publishEvent(new IdentityEvents.EmailVerificationRequested(
                user.getId(),
                user.getName(),
                user.getEmail(),
                token,
                expiresAt,
                LocaleContextHolder.getLocale().getLanguage()));
    }

    private record Row(UUID id, UUID userId, String email, Instant expiresAt, Instant confirmedAt) {
    }
}
