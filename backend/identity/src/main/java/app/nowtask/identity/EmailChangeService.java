package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.EmailChangeView;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.TooManyRequestsException;
import app.nowtask.shared.events.IdentityEvents;

@Service
@Transactional
public class EmailChangeService {

    static final Duration VALIDITY = Duration.ofHours(24);
    private static final int REQUEST_LIMIT_PER_HOUR = 3;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final RegistrationService registrations;
    private final SessionService sessions;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    EmailChangeService(
            JdbcClient jdbc,
            AppUserRepository users,
            RegistrationService registrations,
            SessionService sessions,
            AuditLog audit,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.users = users;
        this.registrations = registrations;
        this.sessions = sessions;
        this.audit = audit;
        this.events = events;
    }

    public EmailChangeView request(UUID userId, String newEmail) {
        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        String cleaned = registrations.email(newEmail);

        if (cleaned.equalsIgnoreCase(user.getEmail())) {
            throw new RuleViolationException("This is already the address on the account", "EMAIL_UNCHANGED");
        }
        if (users.findByEmailIgnoreCase(cleaned).isPresent()) {
            throw new ConflictException("An account with the address " + cleaned + " already exists", "EMAIL_TAKEN");
        }
        if (requestsInLastHour(userId) >= REQUEST_LIMIT_PER_HOUR) {
            throw new TooManyRequestsException("Too many change requests, try again in an hour", 3600);
        }

        cancelOpen(userId);

        String token = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(VALIDITY);

        jdbc.sql("""
                        INSERT INTO email_change (id, user_id, new_email, token_hash, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID(), userId, cleaned, SecureTokens.hash(token), Timestamp.from(expiresAt))
                .update();

        audit.record("account.emailChangeRequested", user.getEmail(),
                Map.of("newEmail", SecureTokens.maskEmail(cleaned)));

        events.publishEvent(new IdentityEvents.EmailChangeRequested(
                userId,
                user.getName(),
                cleaned,
                token,
                expiresAt,
                LocaleContextHolder.getLocale().getLanguage()));

        return new EmailChangeView(cleaned, Instant.now(), expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<EmailChangeView> pending(UUID userId) {
        return jdbc.sql("""
                        SELECT new_email, created_at, expires_at FROM email_change
                        WHERE user_id = ? AND confirmed_at IS NULL AND cancelled_at IS NULL AND expires_at > now()
                        ORDER BY created_at DESC LIMIT 1
                        """)
                .param(userId)
                .query((rs, rowNum) -> new EmailChangeView(
                        rs.getString("new_email"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    public void cancel(UUID userId) {
        cancelOpen(userId);
    }

    public UserView confirm(String token) {
        if (token == null || token.isBlank()) {
            throw new RuleViolationException("The link is invalid", "TOKEN_INVALID");
        }

        Row row = jdbc.sql("""
                        SELECT id, user_id, new_email, expires_at, confirmed_at, cancelled_at
                        FROM email_change WHERE token_hash = ?
                        """)
                .param(SecureTokens.hash(token.trim()))
                .query((rs, rowNum) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("new_email"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant(),
                        rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant()))
                .optional()
                .orElseThrow(() -> new RuleViolationException("The link is invalid", "TOKEN_INVALID"));

        if (row.confirmedAt() != null) {
            throw new RuleViolationException("The link has already been used", "TOKEN_USED");
        }
        if (row.cancelledAt() != null) {
            throw new RuleViolationException("The change has been cancelled", "TOKEN_INVALID");
        }
        if (row.expiresAt().isBefore(Instant.now())) {
            throw new RuleViolationException("The link has expired", "TOKEN_EXPIRED");
        }

        AppUser user = users.findById(row.userId())
                .orElseThrow(() -> new RuleViolationException("The link is invalid", "TOKEN_INVALID"));

        users.findByEmailIgnoreCase(row.newEmail())
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new ConflictException(
                            "An account with the address " + row.newEmail() + " already exists", "EMAIL_TAKEN");
                });

        String previous = user.getEmail();

        user.changeEmail(row.newEmail(), Instant.now());
        users.save(user);

        jdbc.sql("UPDATE email_change SET confirmed_at = now() WHERE id = ?").param(row.id()).update();

        cancelOpen(user.getId());
        sessions.dropAll(user.getId());

        audit.record("account.emailChanged", row.newEmail(),
                Map.of("previousEmail", SecureTokens.maskEmail(previous)));

        events.publishEvent(new IdentityEvents.EmailChanged(
                user.getId(),
                user.getName(),
                previous,
                row.newEmail(),
                Instant.now(),
                LocaleContextHolder.getLocale().getLanguage()));

        return UserDirectoryService.toView(user, null);
    }

    private void cancelOpen(UUID userId) {
        jdbc.sql("""
                        UPDATE email_change SET cancelled_at = now()
                        WHERE user_id = ? AND confirmed_at IS NULL AND cancelled_at IS NULL
                        """)
                .param(userId)
                .update();
    }

    private int requestsInLastHour(UUID userId) {
        Integer count = jdbc.sql("""
                        SELECT count(*) FROM email_change
                        WHERE user_id = ? AND created_at >= now() - INTERVAL '1 hour'
                        """)
                .param(userId)
                .query(Integer.class)
                .single();

        return count == null ? 0 : count;
    }

    private record Row(
            UUID id, UUID userId, String newEmail, Instant expiresAt, Instant confirmedAt, Instant cancelledAt) {
    }
}
