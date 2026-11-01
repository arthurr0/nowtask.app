package app.nowtask.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.SignupResultView;
import app.nowtask.identity.api.SignupResultView.SuggestedOrgView;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class SignupService {

    public static final String MODE_OPEN = "open";
    public static final String MODE_INVITE_ONLY = "invite-only";
    public static final String MODE_SSO_ONLY = "sso-only";

    private final RegistrationService registrations;
    private final EmailVerificationService verifications;
    private final JdbcClient jdbc;
    private final InstanceSettingsService settings;

    SignupService(
            RegistrationService registrations,
            EmailVerificationService verifications,
            JdbcClient jdbc,
            InstanceSettingsService settings) {
        this.registrations = registrations;
        this.verifications = verifications;
        this.jdbc = jdbc;
        this.settings = settings;
    }

    public SignupResultView signup(String name, String email, String password) {
        guardMode();

        UserView user = registrations.register(name, email, password);
        verifications.request(user.id());

        return new SignupResultView(user, suggestOrganizationFor(user.email()).orElse(null));
    }

    public UserView signupFromInvite(String name, String email, String password) {
        return registrations.register(name, email, password, true);
    }

    @Transactional(readOnly = true)
    public String mode() {
        return settings.effectiveSignupMode();
    }

    @Transactional(readOnly = true)
    public Optional<SuggestedOrgView> suggestOrganizationFor(String email) {
        String domain = RegistrationService.domainOf(email == null ? "" : email.toLowerCase(Locale.ROOT));
        if (domain.isBlank()) {
            return Optional.empty();
        }

        return jdbc.sql("SELECT id, name, slug FROM organization_by_sso_domain(?)")
                .param(domain)
                .query((rs, rowNum) -> new SuggestedOrgView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug")))
                .optional();
    }

    private void guardMode() {
        String mode = settings.effectiveSignupMode();

        if (MODE_INVITE_ONLY.equals(mode)) {
            throw new RuleViolationException(
                    "This installation only accepts accounts created from an invitation", "SIGNUP_INVITE_ONLY");
        }
        if (MODE_SSO_ONLY.equals(mode)) {
            throw new RuleViolationException(
                    "This installation only accepts corporate login", "SIGNUP_SSO_ONLY");
        }
    }
}
