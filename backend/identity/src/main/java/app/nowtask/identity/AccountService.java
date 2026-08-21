package app.nowtask.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.EmailChangeView;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.events.IdentityEvents;

@Service
@Transactional
public class AccountService {

    private static final int NAME_LIMIT = 120;
    private static final int INITIALS_LIMIT = 3;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final UserDirectory directory;
    private final Organizations organizations;
    private final RegistrationService registrations;
    private final EmailChangeService emailChanges;
    private final SessionService sessions;
    private final MembershipExitService exits;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    AccountService(
            JdbcClient jdbc,
            AppUserRepository users,
            UserDirectory directory,
            Organizations organizations,
            RegistrationService registrations,
            EmailChangeService emailChanges,
            SessionService sessions,
            MembershipExitService exits,
            PasswordEncoder encoder,
            AuditLog audit,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.users = users;
        this.directory = directory;
        this.organizations = organizations;
        this.registrations = registrations;
        this.emailChanges = emailChanges;
        this.sessions = sessions;
        this.exits = exits;
        this.encoder = encoder;
        this.audit = audit;
        this.events = events;
    }

    public UserView profile(PatchBody patch) {
        AppUser user = currentAccount();

        if (patch.has("name")) {
            String name = required(patch.text("name"), "Full name");
            if (name.length() > NAME_LIMIT) {
                throw new IllegalArgumentException("The name may be at most " + NAME_LIMIT + " characters long");
            }

            user.rename(name, UserNames.shortNameOf(name), UserNames.initialsOf(name));
        }
        if (patch.has("shortName")) {
            user.setShortName(required(patch.text("shortName"), "Display name"));
        }
        if (patch.has("initials")) {
            user.setInitials(initials(patch.text("initials")));
        }

        users.save(user);
        audit.record("account.profile", user.getEmail(), Map.of());

        return directory.currentUser();
    }

    public void changePassword(String currentPassword, String newPassword, String sessionId) {
        AppUser user = currentAccount();

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new RuleViolationException(
                    "The account signs in without a password, use the reset link", "PASSWORD_NOT_SET");
        }
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuleViolationException("The current password is wrong", "PASSWORD_INVALID");
        }

        registrations.password(newPassword);

        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new RuleViolationException("The new password has to differ from the old one", "PASSWORD_REUSED");
        }

        jdbc.sql("UPDATE app_user SET password_hash = ? WHERE id = ?")
                .params(encoder.encode(newPassword), user.getId())
                .update();
        jdbc.sql("UPDATE password_reset SET used_at = now() WHERE user_id = ? AND used_at IS NULL")
                .param(user.getId())
                .update();

        sessions.revokeAllExcept(user.getId(), sessionId);
        audit.record("account.passwordChanged", user.getEmail(), Map.of());

        events.publishEvent(new IdentityEvents.PasswordChanged(
                user.getId(),
                user.getName(),
                user.getEmail(),
                Instant.now(),
                LocaleContextHolder.getLocale().getLanguage()));
    }

    public EmailChangeView requestEmailChange(String password, String newEmail) {
        AppUser user = currentAccount();
        requirePassword(user, password);
        return emailChanges.request(user.getId(), newEmail);
    }

    @Transactional(readOnly = true)
    public Optional<EmailChangeView> pendingEmailChange() {
        return emailChanges.pending(directory.currentUser().id());
    }

    public void cancelEmailChange() {
        emailChanges.cancel(directory.currentUser().id());
    }

    public void leaveOrganization() {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        UserView me = directory.currentUser();
        exits.leave(me.id(), organizationId, me.email());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(String password) {
        UserView me = directory.currentUser();
        AppUser user = users.findById(me.id()).orElseThrow(() -> NotFoundException.of("User", me.id()));

        requirePassword(user, password);

        List<MembershipView> memberships = organizations.membershipsOf(user.getId());

        for (MembershipView membership : memberships) {
            OrganizationContext scope = new OrganizationContext(
                    user.getId(), membership.organizationId(), membership.roleId(), membership.roleCode(),
                    membership.permissions());

            OrganizationContextHolder.runAs(scope,
                    () -> exits.leave(user.getId(), membership.organizationId(), user.getEmail()));
        }

        OrganizationContextHolder.runAs(
                new OrganizationContext(user.getId(), null, null, null, Set.of()),
                () -> {
                    audit.record("account.delete", user.getEmail(), Map.of());
                    exits.anonymize(user.getId());
                });
    }

    private void requirePassword(AppUser user, String password) {
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return;
        }
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new RuleViolationException("The password is wrong", "PASSWORD_INVALID");
        }
    }

    private AppUser currentAccount() {
        UUID id = directory.currentUser().id();
        return users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private static String initials(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Initials are required");
        }

        String cleaned = value.trim().toUpperCase(Locale.ROOT);
        return cleaned.length() > INITIALS_LIMIT ? cleaned.substring(0, INITIALS_LIMIT) : cleaned;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
