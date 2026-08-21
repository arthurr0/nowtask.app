package app.nowtask.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import app.nowtask.identity.PasswordResetService;
import app.nowtask.identity.SessionService;
import app.nowtask.identity.SignupService;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.ApiKeyScope;
import app.nowtask.identity.api.SignupResultView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final UserDirectory directory;
    private final SignupService signups;
    private final PasswordResetService passwordResets;
    private final SessionService sessions;

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository,
            UserDirectory directory,
            SignupService signups,
            PasswordResetService passwordResets,
            SessionService sessions) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.directory = directory;
        this.signups = signups;
        this.passwordResets = passwordResets;
        this.sessions = sessions;
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/login")
    ResponseEntity<UserView> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            openSession(request.email(), request.password(), httpRequest, httpResponse);
            return ResponseEntity.ok(directory.currentUser());
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }

    record SignupRequest(@NotBlank String name, @NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/signup")
    ResponseEntity<SignupResultView> signup(
            @RequestBody SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        SignupResultView created = signups.signup(request.name(), request.email(), request.password());
        openSession(created.user().email(), request.password(), httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    record ForgotPasswordRequest(@NotBlank String email) {
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResets.request(request.email());
        return ResponseEntity.noContent().build();
    }

    record ResetPasswordRequest(@NotBlank String token, @NotBlank String password) {
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResets.reset(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    private void openSession(
            String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            sessions.forget(request.getSession(false).getId());
            request.getSession(false).invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserView me() {
        return directory.currentUser();
    }

    record ApiKeySelf(String prefix, String label, List<String> scopes, UserView owner) {
    }

    @GetMapping("/api-key")
    ResponseEntity<ApiKeySelf> apiKeySelf() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyIdentity identity)) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(new ApiKeySelf(
                identity.prefix(),
                identity.label(),
                ApiKeyScope.ordered(identity.scopes()).stream().map(ApiKeyScope::code).toList(),
                directory.currentUser()));
    }
}
