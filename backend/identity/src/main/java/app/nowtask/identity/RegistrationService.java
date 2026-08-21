package app.nowtask.identity;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class RegistrationService {

    static final int MIN_PASSWORD_LENGTH = 10;

    private static final String DEFAULT_ROLE_CODE = "member";

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password12", "password123", "password1234", "qwertyuiop", "1234567890",
            "12345678910", "iloveyou12", "administrator", "letmein1234", "zaq12wsx34");

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "yopmail.com", "guerrillamail.com", "10minutemail.com", "tempmail.com",
            "temp-mail.org", "throwawaymail.com", "trashmail.com", "getnada.com", "sharklasers.com",
            "dispostable.com", "maildrop.cc", "fakeinbox.com", "mailnesia.com", "spam4.me");

    private final AppUserRepository users;
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AuditLog audit;

    RegistrationService(AppUserRepository users, JdbcClient jdbc, PasswordEncoder encoder, AuditLog audit) {
        this.users = users;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    public UserView register(String name, String email, String password) {
        return register(name, email, password, false);
    }

    public UserView register(String name, String email, String password, boolean emailVerified) {
        String cleanName = required(name, "Full name");
        String cleanEmail = email(email);
        password(password);

        if (users.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            throw new ConflictException("An account with the address " + cleanEmail + " already exists",
                    "EMAIL_TAKEN");
        }

        UUID id = UUID.randomUUID();

        jdbc.sql("""
                INSERT INTO app_user (id, name, short_name, initials, email, password_hash, role, capacity, pending,
                                      email_verified_at, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, false, ?, 'active')
                """)
                .params(id, cleanName, UserNames.shortNameOf(cleanName), UserNames.initialsOf(cleanName),
                        cleanEmail, encoder.encode(password), DEFAULT_ROLE_CODE,
                        emailVerified ? java.sql.Timestamp.from(java.time.Instant.now()) : null)
                .update();

        audit.record("account.signup", cleanEmail, Map.of());

        return users.findById(id)
                .map(user -> UserDirectoryService.toView(user, null))
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));
    }

    String email(String value) {
        String cleaned = required(value, "Email address").toLowerCase(Locale.ROOT);
        if (!cleaned.matches("[^@\\s]+@[^@\\s.]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("The email address is invalid");
        }
        if (DISPOSABLE_DOMAINS.contains(domainOf(cleaned))) {
            throw new RuleViolationException("Use a company address", "EMAIL_DISPOSABLE");
        }
        return cleaned;
    }

    void password(String value) {
        if (value == null || value.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "The password has to be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (COMMON_PASSWORDS.contains(value.toLowerCase(Locale.ROOT))) {
            throw new RuleViolationException("This password is too common", "PASSWORD_TOO_COMMON");
        }
    }

    static String domainOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
