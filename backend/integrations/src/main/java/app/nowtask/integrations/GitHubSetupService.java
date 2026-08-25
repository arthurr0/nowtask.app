package app.nowtask.integrations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.integrations.GitHubRows.Installation;
import app.nowtask.integrations.api.IntegrationViews.GitHubStatusView;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.RuleViolationException;

@Service
public class GitHubSetupService {

    private final GitHubApp app;
    private final GitHubOAuth oauth;
    private final GitHubClient client;
    private final GitHubStore store;
    private final UserDirectory users;

    GitHubSetupService(
            GitHubApp app,
            GitHubOAuth oauth,
            GitHubClient client,
            GitHubStore store,
            UserDirectory users) {
        this.app = app;
        this.oauth = oauth;
        this.client = client;
        this.store = store;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public GitHubStatusView status() {
        if (!app.configured()) {
            return new GitHubStatusView(false, "", false, "", false, List.of(),
                    "The GitHub App is not configured on this instance");
        }

        Optional<Installation> installation = store.current();
        if (installation.isEmpty()) {
            return new GitHubStatusView(true, app.installUrl(), false, "", false, List.of(), "");
        }

        Installation active = installation.get();
        try {
            List<String> repositories = client.repositories(active.installationId());
            return new GitHubStatusView(true, app.installUrl(), true, active.accountLogin(),
                    active.suspended(), repositories, "");
        } catch (GitHubException e) {
            return new GitHubStatusView(true, app.installUrl(), true, active.accountLogin(),
                    active.suspended(), List.of(), e.getMessage());
        }
    }

    @Transactional
    public GitHubStatusView connect(long installationId, String code) {
        if (!app.configured()) {
            throw new RuleViolationException("The GitHub App is not configured on this instance");
        }
        if (installationId <= 0) {
            throw new RuleViolationException("The installation identifier is missing");
        }

        requireOwnership(installationId, code);

        store.lookup(installationId).ifPresent(existing -> {
            if (!existing.organizationId().equals(OrganizationContextHolder.currentOrganizationId())) {
                throw new ForbiddenException("GITHUB_INSTALLATION_TAKEN", String.valueOf(installationId));
            }
            store.release(installationId, Instant.now());
        });

        GitHubAccount account = client.account(installationId);
        store.connect(installationId, account.login(), account.type(), users.currentUser().id());

        return status();
    }

    @Transactional
    public GitHubStatusView disconnect() {
        store.current().ifPresent(installation -> store.release(installation.installationId(), Instant.now()));
        return status();
    }

    private void requireOwnership(long installationId, String code) {
        if (!oauth.available()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new RuleViolationException("The GitHub authorization code is missing");
        }

        if (!oauth.installationsOf(oauth.exchange(code)).contains(installationId)) {
            throw new ForbiddenException("GITHUB_INSTALLATION_FOREIGN", String.valueOf(installationId));
        }
    }
}
