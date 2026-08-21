package app.nowtask.shared;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public record OrganizationContext(
        UUID userId,
        UUID organizationId,
        UUID roleId,
        String roleCode,
        Set<Permission> permissions) {

    public OrganizationContext {
        permissions = permissions == null || permissions.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public static OrganizationContext withoutOrganization(UUID userId) {
        return new OrganizationContext(userId, null, null, null, Set.of());
    }

    public boolean hasOrganization() {
        return organizationId != null;
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            throw ForbiddenException.missingPermission(permission);
        }
    }

    public UUID requireOrganizationId() {
        if (organizationId == null) {
            throw new NoOrganizationException();
        }
        return organizationId;
    }
}
