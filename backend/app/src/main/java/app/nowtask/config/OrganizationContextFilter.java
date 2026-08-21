package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;

class OrganizationContextFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Org-Id";
    static final String SESSION_KEY = "activeOrganizationId";

    private static final List<String> WITHOUT_ORGANIZATION =
            List.of("/api/auth/", "/api/meta", "/api/orgs", "/api/invites/", "/actuator/");

    private final Organizations organizations;
    private final UserDirectory directory;
    private final ApiErrorWriter errors;

    OrganizationContextFilter(Organizations organizations, UserDirectory directory, ApiErrorWriter errors) {
        this.organizations = organizations;
        this.directory = directory;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String anonymous && "anonymousUser".equals(anonymous)) {
            chain.doFilter(request, response);
            return;
        }

        if (authentication.getPrincipal() instanceof ApiKeyIdentity key) {
            applyApiKey(authentication, key, request, response, chain);
            return;
        }

        UUID userId;
        try {
            userId = directory.currentUser().id();
        } catch (RuntimeException e) {
            chain.doFilter(request, response);
            return;
        }

        OrganizationContextHolder.set(OrganizationContext.withoutOrganization(userId));

        try {
            Optional<MembershipView> membership = resolve(request, userId);

            if (membership.isEmpty() && needsOrganization(request)) {
                errors.write(response, 409, "The account does not belong to any organization",
                        java.util.Map.of("code", "NO_ORGANIZATION"));
                return;
            }

            membership.ifPresent(active -> {
                OrganizationContextHolder.set(new OrganizationContext(
                        userId,
                        active.organizationId(),
                        active.roleId(),
                        active.roleCode(),
                        active.permissions()));
                grantAuthorities(authentication, active);
            });

            chain.doFilter(request, response);
        } catch (ForbiddenException e) {
            errors.write(response, 403, e.getMessage(), java.util.Map.of("code", e.errorCode()));
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    private void applyApiKey(
            Authentication authentication,
            ApiKeyIdentity key,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        OrganizationContextHolder.set(new OrganizationContext(
                key.ownerId(), key.organizationId(), key.roleId(), null, java.util.Set.of()));

        Optional<MembershipView> membership;
        try {
            membership = organizations.byRole(key.organizationId(), key.roleId());
        } catch (RuntimeException e) {
            OrganizationContextHolder.clear();
            throw e;
        }

        if (membership.isEmpty()) {
            OrganizationContextHolder.clear();
            errors.write(response, 403, "The key's role no longer exists",
                    java.util.Map.of("code", "ORG_ACCESS_REVOKED"));
            return;
        }

        MembershipView active = membership.get();

        OrganizationContextHolder.set(new OrganizationContext(
                key.ownerId(), active.organizationId(), active.roleId(), active.roleCode(),
                active.permissions()));

        try {
            grantAuthorities(authentication, active);
            chain.doFilter(request, response);
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    private boolean needsOrganization(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return false;
        }
        return WITHOUT_ORGANIZATION.stream().noneMatch(path::startsWith);
    }

    private Optional<MembershipView> resolve(HttpServletRequest request, UUID userId) {
        String header = request.getHeader(HEADER);

        if (header != null && !header.isBlank()) {
            UUID requested;
            try {
                requested = UUID.fromString(header.trim());
            } catch (IllegalArgumentException e) {
                throw new ForbiddenException("ORG_FORBIDDEN", header);
            }
            return Optional.of(organizations.membership(userId, requested)
                    .orElseThrow(() -> new ForbiddenException("ORG_FORBIDDEN", header)));
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_KEY) instanceof UUID stored) {
            Optional<MembershipView> membership = organizations.membership(userId, stored);
            if (membership.isPresent()) {
                return membership;
            }
            session.removeAttribute(SESSION_KEY);
        }

        Optional<MembershipView> preferred = organizations.membershipsOf(userId).stream().findFirst();
        preferred.ifPresent(active -> {
            HttpSession created = request.getSession(true);
            created.setAttribute(SESSION_KEY, active.organizationId());
        });

        return preferred;
    }

    private void grantAuthorities(Authentication authentication, MembershipView membership) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(
                membership.permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.authority()))
                        .toList());

        authentication.getAuthorities().stream()
                .filter(granted -> granted.getAuthority().startsWith("SCOPE_"))
                .forEach(granted -> authorities.add(new SimpleGrantedAuthority(granted.getAuthority())));

        UsernamePasswordAuthenticationToken scoped = UsernamePasswordAuthenticationToken.authenticated(
                authentication.getPrincipal(), authentication.getCredentials(), authorities);
        scoped.setDetails(authentication.getDetails());

        SecurityContextHolder.getContext().setAuthentication(scoped);
    }
}
