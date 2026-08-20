package app.nowtask.identity.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ApiKeys {
    record Issued(String key, ApiKeyView view) {
    }

    List<ApiKeyView> list();

    Issued issue(String label, Set<ApiKeyScope> scopes, Integer expiresInDays);

    void revoke(UUID id);
}
