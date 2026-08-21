package app.nowtask.identity.api;

import java.util.Set;
import java.util.UUID;
import app.nowtask.shared.Permission;

public record RoleView(
        UUID id,
        String code,
        String name,
        int position,
        boolean isProtected,
        Set<Permission> permissions,
        int memberCount) {
}
