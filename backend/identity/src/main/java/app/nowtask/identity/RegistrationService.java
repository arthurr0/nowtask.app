package app.nowtask.identity;

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
import app.nowtask.shared.RoleId;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class RegistrationService {

    static final int MIN_PASSWORD_LENGTH = 10;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password12", "password123", "password1234", "qwertyuiop", "1234567890",
            "12345678910", "iloveyou12", "administrator", "letmein1234", "zaq12wsx34");

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
        String cleanName = required(name, "Full name");
        String cleanEmail = email(email);
        password(password);

        if (users.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            throw new ConflictException("An account with the address " + cleanEmail + " already exists");
        }

        UUID id = UUID.randomUUID();

        jdbc.sql("""
                INSERT INTO app_user (id, name, short_name, initials, email, password_hash, role, capacity, pending)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, false)
                """)
                .params(id, cleanName, UserNames.shortNameOf(cleanName), UserNames.initialsOf(cleanName),
                        cleanEmail, encoder.encode(password), RoleId.MEMBER.code())
                .update();

        audit.record("account.signup", cleanEmail, Map.of("role", RoleId.MEMBER.code()));

        return users.findById(id)
                .map(UserDirectoryService::toView)
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));
    }

    private String email(String value) {
        String cleaned = required(value, "Email address").toLowerCase();
        if (!cleaned.matches("[^@\\s]+@[^@\\s.]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("The email address is invalid");
        }
        return cleaned;
    }

    private void password(String value) {
        if (value == null || value.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The password has to be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (COMMON_PASSWORDS.contains(value.toLowerCase())) {
            throw new RuleViolationException("This password is too common");
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
