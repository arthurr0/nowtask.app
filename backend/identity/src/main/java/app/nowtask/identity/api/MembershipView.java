package app.nowtask.identity.api;

import java.util.Set;
import java.util.UUID;
import app.nowtask.shared.Permission;

public record MembershipView(
        UUID organizationId,
        String name,
        String slug,
        String state,
        UUID roleId,
        String roleCode,
        String roleName,
        Set<Permission> permissions) {
}
