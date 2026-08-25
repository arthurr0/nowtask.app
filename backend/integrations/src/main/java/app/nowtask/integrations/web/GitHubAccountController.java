package app.nowtask.integrations.web;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.integrations.GitHubAccountService;
import app.nowtask.integrations.api.IntegrationViews.GitHubAccountView;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/integrations/github/account")
class GitHubAccountController {

    private static final String STATE = "githubOauthState";

    private final GitHubAccountService accounts;

    GitHubAccountController(GitHubAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    GitHubAccountView status() {
        return accounts.status();
    }

    @PostMapping("/authorize")
    Map<String, String> authorize(HttpSession session) {
        String state = accounts.newState();
        session.setAttribute(STATE, state);
        return Map.of("url", accounts.authorizeUrl(state));
    }

    @PostMapping("/connect")
    GitHubAccountView connect(@RequestBody Map<String, Object> body, HttpSession session) {
        PatchBody patch = new PatchBody(body);
        Object expected = session.getAttribute(STATE);
        session.removeAttribute(STATE);

        return accounts.connect(
                patch.text("code"), patch.text("state"), expected == null ? null : expected.toString());
    }

    @DeleteMapping
    GitHubAccountView disconnect() {
        return accounts.disconnect();
    }
}
