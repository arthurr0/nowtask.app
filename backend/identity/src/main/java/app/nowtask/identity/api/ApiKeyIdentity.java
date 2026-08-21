package app.nowtask.identity.api;

import java.util.Set;
import java.util.UUID;

public record ApiKeyIdentity(
        UUID keyId,
        UUID organizationId,
        UUID roleId,
        String prefix,
        String label,
        UUID ownerId,
        String ownerEmail,
        Set<ApiKeyScope> scopes) {

    public boolean has(ApiKeyScope scope) {
        return scopes.contains(scope);
    }

    public String agentLabel() {
        return "agent:" + label + " (" + prefix + ")";
    }
}
