package app.nowtask.integrations;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.integrations.GitHubAccounts.Row;
import app.nowtask.integrations.api.IntegrationViews.GitHubAccountView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.RuleViolationException;

@Service
public class GitHubAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitHubApp app;
    private final GitHubOAuth oauth;
    private final GitHubAccounts accounts;
    private final UserDirectory users;
    private final String appUrl;

    GitHubAccountService(
            GitHubApp app,
            GitHubOAuth oauth,
            GitHubAccounts accounts,
            UserDirectory users,
            @Value("${nowtask.app.url:http://localhost:8080}") String appUrl) {
        this.app = app;
        this.oauth = oauth;
        this.accounts = accounts;
        this.users = users;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    @Transactional(readOnly = true)
    public GitHubAccountView status() {
        if (!available()) {
            return new GitHubAccountView(false, false, "", "");
        }

        return accounts.of(users.currentUser().id())
                .map(row -> new GitHubAccountView(true, true, row.login(), row.avatarUrl()))
                .orElseGet(() -> new GitHubAccountView(true, false, "", ""));
    }

    public String newState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String authorizeUrl(String state) {
        if (!available()) {
            throw new RuleViolationException("This instance has no GitHub App to authorize against");
        }
        return oauth.authorizeUrl(state, appUrl + "/app/settings");
    }

    @Transactional
    public GitHubAccountView connect(String code, String state, String expectedState) {
        if (!available()) {
            throw new RuleViolationException("This instance has no GitHub App to authorize against");
        }
        if (code == null || code.isBlank()) {
            throw new RuleViolationException("The GitHub authorization code is missing");
        }
        if (expectedState == null || expectedState.isBlank() || !expectedState.equals(state)) {
            throw new ForbiddenException("GITHUB_STATE_MISMATCH", "state");
        }

        UUID userId = users.currentUser().id();
        GitHubUser account = oauth.identity(oauth.exchange(code));

        if (accounts.takenByAnother(userId, account.id())) {
            throw new ConflictException("The GitHub account " + account.login()
                    + " is already linked to another person here");
        }

        accounts.connect(userId, account);
        return new GitHubAccountView(true, true, account.login(), account.avatarUrl());
    }

    @Transactional
    public GitHubAccountView disconnect() {
        accounts.disconnect(users.currentUser().id());
        return status();
    }

    Optional<Row> of(UUID userId) {
        return accounts.of(userId);
    }

    private boolean available() {
        return app.configured() && oauth.available();
    }
}
