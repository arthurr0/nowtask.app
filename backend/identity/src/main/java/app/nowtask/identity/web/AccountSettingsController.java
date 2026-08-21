package app.nowtask.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.AccountService;
import app.nowtask.identity.SessionService;
import app.nowtask.identity.api.EmailChangeView;
import app.nowtask.identity.api.SessionView;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/account")
class AccountSettingsController {

    private final AccountService account;
    private final SessionService sessions;

    AccountSettingsController(AccountService account, SessionService sessions) {
        this.account = account;
        this.sessions = sessions;
    }

    @PatchMapping("/profile")
    UserView profile(@RequestBody Map<String, Object> body) {
        return account.profile(new PatchBody(body));
    }

    record PasswordRequest(String currentPassword, @NotBlank String newPassword) {
    }

    @PostMapping("/password")
    ResponseEntity<Void> password(@RequestBody PasswordRequest request, HttpServletRequest http) {
        account.changePassword(request.currentPassword(), request.newPassword(), sessionId(http));
        return ResponseEntity.noContent().build();
    }

    record EmailRequest(@NotBlank String email, String password) {
    }

    @PostMapping("/email")
    EmailChangeView requestEmailChange(@RequestBody EmailRequest request) {
        return account.requestEmailChange(request.password(), request.email());
    }

    @GetMapping("/email-change")
    ResponseEntity<EmailChangeView> pendingEmailChange() {
        return account.pendingEmailChange()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/email-change")
    ResponseEntity<Void> cancelEmailChange() {
        account.cancelEmailChange();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    List<SessionView> listSessions(HttpServletRequest http) {
        return sessions.list(sessionId(http));
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revokeSession(@PathVariable UUID id, HttpServletRequest http) {
        sessions.revoke(id, sessionId(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    Map<String, Integer> revokeOtherSessions(HttpServletRequest http) {
        return Map.of("closed", sessions.revokeOthers(sessionId(http)));
    }

    @PostMapping("/leave")
    ResponseEntity<Void> leave() {
        account.leaveOrganization();
        return ResponseEntity.noContent().build();
    }

    record DeleteRequest(String password) {
    }

    @PostMapping("/delete")
    ResponseEntity<Void> delete(@RequestBody DeleteRequest request, HttpServletRequest http) {
        account.delete(request.password());
        endSession(http);
        return ResponseEntity.noContent().build();
    }

    private static String sessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }

    private static void endSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
