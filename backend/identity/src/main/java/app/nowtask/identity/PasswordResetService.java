package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.events.IdentityEvents;

@Service
@Transactional
public class PasswordResetService {

    static final Duration VALIDITY = Duration.ofHours(1);
    private static final int REQUEST_LIMIT_PER_HOUR = 3;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final RegistrationService registrations;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    PasswordResetService(
            JdbcClient jdbc,
            AppUserRepository users,
            RegistrationService registrations,
            PasswordEncoder encoder,
            AuditLog audit,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.users = users;
        this.registrations = registrations;
        this.encoder = encoder;
        this.audit = audit;
        this.events = events;
    }

    public void request(String email) {
        String cleaned = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return;
        }

        Optional<AppUser> found = users.findByEmailIgnoreCase(cleaned);
        if (found.isEmpty()) {
            return;
        }

        AppUser user = found.get();
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return;
        }
        if (!"active".equals(user.getState())) {
            return;
        }
        if (requestsInLastHour(user.getId()) >= REQUEST_LIMIT_PER_HOUR) {
            return;
        }

        String token = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(VALIDITY);

        jdbc.sql("""
                        INSERT INTO password_reset (id, user_id, email, token_hash, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID(), user.getId(), user.getEmail(), SecureTokens.hash(token),
                        Timestamp.from(expiresAt))
                .update();

        audit.record("account.passwordResetRequested", user.getEmail(), Map.of());

        events.publishEvent(new IdentityEvents.PasswordResetRequested(
                user.getId(),
                user.getName(),
                user.getEmail(),
                token,
                expiresAt,
                LocaleContextHolder.getLocale().getLanguage()));
    }

    public void reset(String token, String password) {
        if (token == null || token.isBlank()) {
            throw new RuleViolationException("The link is invalid", "TOKEN_INVALID");
        }

        Row row = jdbc.sql("""
                        SELECT id, user_id, expires_at, used_at
                        FROM password_reset WHERE token_hash = ?
                        """)
                .param(SecureTokens.hash(token.trim()))
                .query((rs, rowNum) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()))
                .optional()
                .orElseThrow(() -> new RuleViolationException("The link is invalid", "TOKEN_INVALID"));

        if (row.usedAt() != null) {
            throw new RuleViolationException("The link has already been used", "TOKEN_USED");
        }
        if (row.expiresAt().isBefore(Instant.now())) {
            throw new RuleViolationException("The link has expired", "TOKEN_EXPIRED");
        }

        registrations.password(password);

        AppUser user = users.findById(row.userId())
                .orElseThrow(() -> new RuleViolationException("The link is invalid", "TOKEN_INVALID"));

        jdbc.sql("UPDATE app_user SET password_hash = ? WHERE id = ?")
                .params(encoder.encode(password), user.getId())
                .update();

        jdbc.sql("UPDATE password_reset SET used_at = now() WHERE user_id = ? AND used_at IS NULL")
                .param(user.getId())
                .update();

        audit.record("account.passwordReset", user.getEmail(), Map.of());

        events.publishEvent(new IdentityEvents.PasswordChanged(
                user.getId(),
                user.getName(),
                user.getEmail(),
                Instant.now(),
                LocaleContextHolder.getLocale().getLanguage()));
    }

    private int requestsInLastHour(UUID userId) {
        Integer count = jdbc.sql("""
                        SELECT count(*) FROM password_reset
                        WHERE user_id = ? AND created_at >= now() - INTERVAL '1 hour'
                        """)
                .param(userId)
                .query(Integer.class)
                .single();

        return count == null ? 0 : count;
    }

    private record Row(UUID id, UUID userId, Instant expiresAt, Instant usedAt) {
    }
}
