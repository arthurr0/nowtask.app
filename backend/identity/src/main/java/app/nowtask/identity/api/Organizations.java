package app.nowtask.identity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Organizations {
    OrganizationView create(UUID creatorId, String name, String slug, String presetCode);

    boolean slugAvailable(String slug);

    OrganizationView update(app.nowtask.shared.PatchBody patch);

    List<MembershipView> membershipsOf(UUID userId);

    Optional<MembershipView> membership(UUID userId, UUID organizationId);

    Optional<MembershipView> byRole(UUID organizationId, UUID roleId);

    Optional<UUID> preferredOrganization(UUID userId);

    Optional<OrganizationView> current();

    void markSeen(UUID userId, UUID organizationId);
}
