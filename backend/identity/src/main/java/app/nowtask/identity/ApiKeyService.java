package app.nowtask.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import app.nowtask.identity.api.ApiKeyAuthenticator;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.ApiKeyScope;
import app.nowtask.identity.api.ApiKeyView;
import app.nowtask.identity.api.ApiKeys;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;

@Service
class ApiKeyService implements ApiKeys, ApiKeyAuthenticator {
    private static final int MAX_EXPIRY_DAYS = 3650;

    private final ApiKeyRepository keys;
    private final AppUserRepository users;
    private final UserDirectory directory;

    ApiKeyService(ApiKeyRepository keys, AppUserRepository users, UserDirectory directory) {
        this.keys = keys;
        this.users = users;
        this.directory = directory;
    }

    @Override
    public List<ApiKeyView> list() {
        Instant now = Instant.now();
        return keys.findAll().stream().map(row -> toView(row, now)).toList();
    }

    @Override
    public Issued issue(String label, Set<ApiKeyScope> scopes, Integer expiresInDays) {
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("The key name is required");
        }
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > MAX_EXPIRY_DAYS)) {
            throw new RuleViolationException("Key validity has to be between 1 and " + MAX_EXPIRY_DAYS + " days");
        }

        Set<ApiKeyScope> granted = scopes == null || scopes.isEmpty() ? ApiKeyScope.DEFAULT_GRANT : scopes;
        UUID ownerId = directory.currentUser().id();
        Instant now = Instant.now();
        String token = ApiKeyTokens.generate();

        ApiKeyRow row = new ApiKeyRow(
                UUID.randomUUID(),
                ApiKeyTokens.prefixOf(token),
                trimmed,
                ApiKeyTokens.hash(token),
                ApiKeyScope.join(granted),
                ownerId,
                null,
                now,
                expiresInDays == null ? null : now.plus(Duration.ofDays(expiresInDays)),
                null);

        keys.insert(row, ownerId);
        return new Issued(token, toView(row, now));
    }

    @Override
    public void revoke(UUID id) {
        if (keys.findById(id).isEmpty()) {
            throw NotFoundException.of("API key", id.toString());
        }
        keys.revoke(id, Instant.now());
    }

    @Override
    public Optional<ApiKeyIdentity> authenticate(String presentedToken) {
        if (!ApiKeyTokens.looksLikeApiKey(presentedToken)) {
            return Optional.empty();
        }

        Optional<ApiKeyRow> found = keys.findByPrefix(ApiKeyTokens.prefixOf(presentedToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ApiKeyRow row = found.get();
        Instant now = Instant.now();
        if (!ApiKeyTokens.matches(row.tokenHash(), presentedToken)
                || row.revoked()
                || row.expired(now)
                || row.ownerId() == null) {
            return Optional.empty();
        }

        Optional<AppUser> owner = users.findById(row.ownerId());
        if (owner.isEmpty() || owner.get().isPending()) {
            return Optional.empty();
        }

        keys.touch(row.id(), now);
        return Optional.of(new ApiKeyIdentity(
                row.id(),
                row.prefix(),
                row.label(),
                row.ownerId(),
                owner.get().getEmail(),
                ApiKeyScope.parse(row.scopes())));
    }

    private static ApiKeyView toView(ApiKeyRow row, Instant now) {
        return new ApiKeyView(
                row.id(),
                row.prefix(),
                row.label(),
                row.lastUsedAt(),
                ApiKeyScope.ordered(ApiKeyScope.parse(row.scopes())),
                row.ownerId(),
                row.createdAt(),
                row.expiresAt(),
                row.revokedAt(),
                row.state(now));
    }
}
