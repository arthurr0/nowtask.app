package app.nowtask.identity.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.EmailVerificationService;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;

@RestController
@RequestMapping("/api/auth")
class AccountController {

    private final EmailVerificationService verifications;
    private final UserDirectory directory;

    AccountController(EmailVerificationService verifications, UserDirectory directory) {
        this.verifications = verifications;
        this.directory = directory;
    }

    record VerifyRequest(@NotBlank String token) {
    }

    @PostMapping("/verify-email")
    UserView verifyEmail(@RequestBody VerifyRequest request) {
        return verifications.confirm(request.token());
    }

    @PostMapping("/resend-verification")
    ResponseEntity<Void> resendVerification() {
        verifications.resend(directory.currentUser().id());
        return ResponseEntity.noContent().build();
    }
}
