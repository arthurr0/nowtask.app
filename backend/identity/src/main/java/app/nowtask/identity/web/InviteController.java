package app.nowtask.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.InviteService;
import app.nowtask.identity.OnboardingService;
import app.nowtask.identity.SignupService;
import app.nowtask.identity.api.InvitePreviewView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.RuleViolationException;

@RestController
@RequestMapping("/api/invites")
class InviteController {

    private final InviteService invites;
    private final SignupService signups;
    private final OnboardingService onboarding;
    private final UserDirectory directory;
    private final SessionAuthenticator sessions;

    InviteController(
            InviteService invites,
            SignupService signups,
            OnboardingService onboarding,
            UserDirectory directory,
            SessionAuthenticator sessions) {
        this.invites = invites;
        this.signups = signups;
        this.onboarding = onboarding;
        this.directory = directory;
        this.sessions = sessions;
    }

    @GetMapping("/{token}")
    InvitePreviewView preview(@PathVariable String token) {
        return invites.preview(token);
    }

    record AcceptRequest(String name, String password) {
    }

    record AcceptedView(UUID organizationId, String organizationName, String roleCode, UserView user) {
    }

    @PostMapping("/{token}/accept")
    ResponseEntity<AcceptedView> accept(
            @PathVariable String token,
            @RequestBody(required = false) AcceptRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        InviteService.Target target = invites.target(token)
                .orElseThrow(() -> new NotFoundException("Unknown invitation"));

        switch (target.state()) {
            case "expired" -> throw new RuleViolationException("The invitation has expired", "INVITE_EXPIRED");
            case "revoked", "accepted" -> throw new RuleViolationException(
                    "The invitation is no longer valid", "INVITE_" + target.state().toUpperCase(java.util.Locale.ROOT));
            default -> {
            }
        }

        AcceptRequest body = request == null ? new AcceptRequest(null, null) : request;
        UUID userId = signedInUser()
                .map(user -> {
                    if (!user.email().equalsIgnoreCase(target.email())) {
                        throw new ConflictException(
                                "You are signed in with a different address than the invitation", "EMAIL_MISMATCH");
                    }
                    return user.id();
                })
                .orElseGet(() -> authenticateOrRegister(target, body, httpRequest, httpResponse));

        InviteService.Accepted accepted = OrganizationContextHolder.currentOrNull() != null
                && target.organizationId().equals(OrganizationContextHolder.currentOrNull().organizationId())
                ? invites.accept(token, userId)
                : acceptInOrganizationScope(token, userId, target.organizationId());

        sessions.selectOrganization(httpRequest, accepted.organizationId());

        return ResponseEntity.ok(new AcceptedView(
                accepted.organizationId(), target.organizationName(), accepted.roleCode(), directory.currentUser()));
    }

    @PostMapping("/{token}/request-new")
    ResponseEntity<Void> requestNew(@PathVariable String token) {
        invites.requestNew(token);
        return ResponseEntity.noContent().build();
    }

    private InviteService.Accepted acceptInOrganizationScope(String token, UUID userId, UUID organizationId) {
        OrganizationContext previous = OrganizationContextHolder.currentOrNull();
        OrganizationContextHolder.set(new OrganizationContext(userId, organizationId, null, null, Set.of()));

        try {
            InviteService.Accepted accepted = invites.accept(token, userId);
            onboarding.start(organizationId, userId, OnboardingService.FLOW_INVITEE, OnboardingService.STEP_TOUR);
            return accepted;
        } finally {
            if (previous == null) {
                OrganizationContextHolder.clear();
            } else {
                OrganizationContextHolder.set(previous);
            }
        }
    }

    private UUID authenticateOrRegister(
            InviteService.Target target,
            AcceptRequest body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (body.password() == null || body.password().isBlank()) {
            throw new IllegalArgumentException("The password is required");
        }

        if (target.accountExists()) {
            try {
                sessions.openSession(target.email(), body.password(), httpRequest, httpResponse);
            } catch (BadCredentialsException e) {
                throw new ConflictException("The password is incorrect", "BAD_CREDENTIALS");
            }
            return target.userId();
        }

        UserView created = signups.signupFromInvite(body.name(), target.email(), body.password());
        sessions.openSession(target.email(), body.password(), httpRequest, httpResponse);
        return created.id();
    }

    private Optional<UserView> signedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String) {
            return Optional.empty();
        }

        try {
            return Optional.of(directory.currentUser());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
