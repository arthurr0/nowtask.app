package app.nowtask.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.RoleRefView;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

    private static final String MEMBERS = """
            SELECT u.id, u.name, u.short_name, u.initials, u.email, u.email_verified_at,
                   m.capacity, m.state AS member_state,
                   r.id AS role_id, r.code AS role_code, r.name AS role_name
            FROM organization_member m
            JOIN app_user u          ON u.id = m.user_id
            JOIN organization_role r ON r.id = m.role_id
            WHERE m.organization_id = ?
            """;

    private final AppUserRepository users;
    private final JdbcClient jdbc;

    UserDirectoryService(AppUserRepository users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    public List<UserView> findAll() {
        UUID organizationId = activeOrganization();
        if (organizationId == null) {
            return List.of();
        }

        List<UserView> members = jdbc.sql(MEMBERS + " ORDER BY m.state, u.name")
                .param(organizationId)
                .query((rs, rowNum) -> new UserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("short_name"),
                        rs.getString("initials"),
                        rs.getString("email"),
                        new RoleRefView(
                                rs.getObject("role_id", UUID.class),
                                rs.getString("role_code"),
                                rs.getString("role_name")),
                        rs.getInt("capacity"),
                        "disabled".equals(rs.getString("member_state")),
                        null,
                        rs.getTimestamp("email_verified_at") != null))
                .list();

        List<UserView> all = new ArrayList<>(members);
        all.addAll(openInvitesAsUsers(organizationId));
        return all;
    }

    @Override
    public List<UserView> findActive() {
        return findAll().stream().filter(user -> !user.pending()).toList();
    }

    @Override
    public Optional<UserView> findById(UUID id) {
        return findAll().stream()
                .filter(user -> user.id().equals(id))
                .findFirst()
                .or(() -> users.findById(id).map(user -> toView(user, null)));
    }

    @Override
    public Map<UUID, UserView> findByIds(Iterable<UUID> ids) {
        List<UUID> wanted = new ArrayList<>();
        ids.forEach(wanted::add);

        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserView> byId = new LinkedHashMap<>();
        for (UserView user : findAll()) {
            if (wanted.contains(user.id())) {
                byId.put(user.id(), user);
            }
        }

        for (UUID id : wanted) {
            if (!byId.containsKey(id)) {
                users.findById(id).ifPresent(user -> byId.put(id, toView(user, null)));
            }
        }

        return byId;
    }

    @Override
    public List<TeamView> findTeams() {
        return jdbc.sql("SELECT id, name, headcount FROM team ORDER BY name")
                .query((rs, rowNum) -> new TeamView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("headcount")))
                .list();
    }

    @Override
    public UserView currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NotFoundException("No signed-in user");
        }

        AppUser user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> NotFoundException.of("User", authentication.getName()));

        return toView(user, currentRole(user.getId()));
    }

    private List<UserView> openInvitesAsUsers(UUID organizationId) {
        return jdbc.sql("""
                        SELECT i.id, i.email, i.created_at,
                               r.id AS role_id, r.code AS role_code, r.name AS role_name
                        FROM organization_invite i
                        JOIN organization_role r ON r.id = i.role_id
                        WHERE i.organization_id = ? AND i.state = 'open'
                        ORDER BY i.created_at DESC
                        """)
                .param(organizationId)
                .query((rs, rowNum) -> new UserView(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("email"),
                        UserNames.initialsOf(rs.getString("email")),
                        rs.getString("email"),
                        new RoleRefView(
                                rs.getObject("role_id", UUID.class),
                                rs.getString("role_code"),
                                rs.getString("role_name")),
                        0,
                        true,
                        rs.getTimestamp("created_at").toLocalDateTime().toLocalDate(),
                        false))
                .list();
    }

    private RoleRefView currentRole(UUID userId) {
        UUID organizationId = activeOrganization();
        if (organizationId == null) {
            return null;
        }

        return jdbc.sql("""
                        SELECT r.id, r.code, r.name
                        FROM organization_member m
                        JOIN organization_role r ON r.id = m.role_id
                        WHERE m.organization_id = ? AND m.user_id = ?
                        """)
                .params(organizationId, userId)
                .query((rs, rowNum) -> new RoleRefView(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name")))
                .optional()
                .orElse(null);
    }

    private static UUID activeOrganization() {
        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        return context == null ? null : context.organizationId();
    }

    static UserView toView(AppUser user, RoleRefView role) {
        return new UserView(
                user.getId(),
                user.getName(),
                user.getShortName(),
                user.getInitials(),
                user.getEmail(),
                role,
                user.getCapacity(),
                user.isPending(),
                user.getInvitedOn(),
                user.isEmailVerified());
    }
}
