package app.nowtask.identity;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.RoleView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.Permission;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class RoleService {

    static final Set<Permission> KEYS_TO_THE_ORGANIZATION =
            Set.of(Permission.MEMBERS_MANAGE, Permission.ROLES_MANAGE);

    private final JdbcClient jdbc;
    private final OrganizationService organizations;
    private final AuditLog audit;

    RoleService(JdbcClient jdbc, OrganizationService organizations, AuditLog audit) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        List<Def> defs = jdbc.sql("""
                        SELECT r.id, r.code, r.name, r.position, r.protected,
                               (SELECT count(*) FROM organization_member m WHERE m.role_id = r.id) AS member_count
                        FROM organization_role r
                        WHERE r.organization_id = ?
                        ORDER BY r.position, r.name
                        """)
                .param(OrganizationContextHolder.currentOrganizationId())
                .query((rs, rowNum) -> new Def(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("position"),
                        rs.getBoolean("protected"),
                        rs.getInt("member_count")))
                .list();

        Map<UUID, Set<Permission>> permissions = organizations.permissionsOf(defs.stream().map(Def::id).toList());

        return defs.stream()
                .map(def -> new RoleView(def.id(), def.code(), def.name(), def.position(), def.isProtected(),
                        permissions.getOrDefault(def.id(), Set.of()), def.memberCount()))
                .toList();
    }

    public RoleView create(String code, String name, List<String> permissions) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        String cleanCode = required(code, "Role code").toLowerCase();
        String cleanName = required(name, "Role name");

        if (!cleanCode.matches("[a-z0-9][a-z0-9_-]{0,38}")) {
            throw new IllegalArgumentException("The role code may contain only lowercase letters, digits, - and _");
        }
        if (exists(organizationId, cleanCode)) {
            throw new ConflictException("A role with code " + cleanCode + " already exists");
        }

        UUID id = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM organization_role WHERE organization_id = ?")
                .param(organizationId)
                .query(Integer.class)
                .single();

        jdbc.sql("""
                        INSERT INTO organization_role (id, organization_id, code, name, position, protected)
                        VALUES (?, ?, ?, ?, ?, FALSE)
                        """)
                .params(id, organizationId, cleanCode, cleanName, position)
                .update();

        replacePermissions(id, parse(permissions));
        audit.record("role.create", cleanCode, Map.of("name", cleanName));

        return requireRole(id);
    }

    public RoleView update(UUID id, PatchBody patch) {
        RoleView role = requireRole(id);

        if (patch.has("name")) {
            jdbc.sql("UPDATE organization_role SET name = ? WHERE id = ?")
                    .params(required(patch.text("name"), "Role name"), id)
                    .update();
        }
        if (patch.has("position")) {
            Integer position = patch.number("position");
            jdbc.sql("UPDATE organization_role SET position = ? WHERE id = ?")
                    .params(position == null ? 0 : position, id)
                    .update();
        }
        if (patch.has("permissions")) {
            Set<Permission> next = parse(patch.strings("permissions"));

            if (role.isProtected() && !next.containsAll(KEYS_TO_THE_ORGANIZATION)) {
                throw new RuleViolationException(
                        "A protected role cannot lose members.manage or roles.manage");
            }

            replacePermissions(id, next);
            guardTheKeysStayInside();
            audit.record("role.permissions", role.code(),
                    Map.of("permissions", next.stream().map(Permission::code).sorted().toList()));
        }

        return requireRole(id);
    }

    public void delete(UUID id, UUID reassignTo) {
        RoleView role = requireRole(id);

        if (role.isProtected()) {
            throw new RuleViolationException("A protected role cannot be deleted");
        }
        if (role.memberCount() > 0) {
            if (reassignTo == null) {
                throw new RuleViolationException(
                        "The role still has " + role.memberCount() + " members, pass reassignTo");
            }
            if (reassignTo.equals(id)) {
                throw new IllegalArgumentException("Members cannot be moved to the role being deleted");
            }

            requireRole(reassignTo);
            jdbc.sql("UPDATE organization_member SET role_id = ? WHERE role_id = ?")
                    .params(reassignTo, id)
                    .update();
        }

        jdbc.sql("DELETE FROM organization_role WHERE id = ?").param(id).update();
        guardTheKeysStayInside();
        audit.record("role.delete", role.code(), Map.of());
    }

    public void guardTheKeysStayInside() {
        int holders = jdbc.sql("""
                        SELECT count(*)
                        FROM organization_member m
                        WHERE m.organization_id = ?
                          AND m.state = 'active'
                          AND (SELECT count(*) FROM organization_role_permission p
                               WHERE p.role_id = m.role_id AND p.permission IN ('members.manage', 'roles.manage')) = 2
                        """)
                .param(OrganizationContextHolder.currentOrganizationId())
                .query(Integer.class)
                .single();

        if (holders < 1) {
            throw new RuleViolationException(
                    "The organization would be left with nobody able to manage members and roles");
        }
    }

    RoleView requireRole(UUID id) {
        return list().stream()
                .filter(role -> role.id().equals(id))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Role", id.toString()));
    }

    UUID roleIdByCode(UUID organizationId, String code) {
        return jdbc.sql("SELECT id FROM organization_role WHERE organization_id = ? AND code = ?")
                .params(organizationId, code)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> NotFoundException.of("Role", code));
    }

    private boolean exists(UUID organizationId, String code) {
        return jdbc.sql("SELECT count(*) FROM organization_role WHERE organization_id = ? AND code = ?")
                .params(organizationId, code)
                .query(Integer.class)
                .single() > 0;
    }

    private void replacePermissions(UUID roleId, Set<Permission> permissions) {
        jdbc.sql("DELETE FROM organization_role_permission WHERE role_id = ?").param(roleId).update();

        for (Permission permission : permissions) {
            jdbc.sql("INSERT INTO organization_role_permission (role_id, permission) VALUES (?, ?)")
                    .params(roleId, permission.code())
                    .update();
        }
    }

    private Set<Permission> parse(List<String> codes) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (codes != null) {
            new LinkedHashSet<>(codes).forEach(code -> permissions.add(Permission.of(code)));
        }
        return permissions;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    record Def(UUID id, String code, String name, int position, boolean isProtected, int memberCount) {
    }
}
