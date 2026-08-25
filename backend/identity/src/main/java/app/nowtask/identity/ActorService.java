package app.nowtask.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import app.nowtask.identity.api.Actors;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.ActorContext;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;

@Service
class ActorService implements Actors {

    private final Organizations organizations;
    private final UserDirectory users;

    ActorService(Organizations organizations, UserDirectory users) {
        this.organizations = organizations;
        this.users = users;
    }

    @Override
    public boolean runAs(UUID userId, UUID organizationId, String label, Runnable work) {
        if (userId == null || organizationId == null) {
            return false;
        }

        SecurityContext previousSecurity = SecurityContextHolder.getContext();
        OrganizationContext previousOrganization = OrganizationContextHolder.currentOrNull();

        OrganizationContextHolder.set(new OrganizationContext(userId, organizationId, null, null, Set.of()));

        try {
            Optional<MembershipView> membership = organizations.membership(userId, organizationId);
            Optional<UserView> user = users.findById(userId);

            if (membership.isEmpty() || user.isEmpty()) {
                return false;
            }

            MembershipView active = membership.get();
            OrganizationContextHolder.set(new OrganizationContext(
                    userId, active.organizationId(), active.roleId(), active.roleCode(), active.permissions()));
            SecurityContextHolder.setContext(authenticated(user.get(), active));

            ActorContext.runAs(label, work);
            return true;
        } finally {
            SecurityContextHolder.setContext(previousSecurity);
            if (previousOrganization == null) {
                OrganizationContextHolder.clear();
            } else {
                OrganizationContextHolder.set(previousOrganization);
            }
        }
    }

    private static SecurityContext authenticated(UserView user, MembershipView membership) {
        List<SimpleGrantedAuthority> authorities = membership.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.authority()))
                .toList();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.email(), null, authorities));
        return context;
    }
}
