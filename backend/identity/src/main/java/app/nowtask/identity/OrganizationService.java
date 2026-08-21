package app.nowtask.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.OrganizationView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.Permission;

@Service
@Transactional(readOnly = true)
public class OrganizationService implements Organizations {

    private static final String MEMBERSHIP_SQL = """
            SELECT o.id           AS organization_id,
                   o.name         AS organization_name,
                   o.slug         AS slug,
                   o.state        AS organization_state,
                   m.state        AS member_state,
                   r.id           AS role_id,
                   r.code         AS role_code,
                   r.name         AS role_name
            FROM organization_member m
            JOIN organization o      ON o.id = m.organization_id
            JOIN organization_role r ON r.id = m.role_id
            WHERE m.user_id = ?
              AND m.state = 'active'
              AND o.state = 'active'
            """;

    private final JdbcClient jdbc;
    private final AuditLog audit;

    OrganizationService(JdbcClient jdbc, AuditLog audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public List<MembershipView> membershipsOf(UUID userId) {
        List<Row> rows = jdbc.sql(MEMBERSHIP_SQL + " ORDER BY m.last_seen_at DESC NULLS LAST, o.name")
                .param(userId)
                .query((rs, rowNum) -> new Row(
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_name"),
                        rs.getString("slug"),
                        rs.getString("organization_state"),
                        rs.getString("member_state"),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"),
                        rs.getString("role_name")))
                .list();

        return withPermissions(rows);
    }

    @Override
    public Optional<MembershipView> membership(UUID userId, UUID organizationId) {
        List<Row> rows = jdbc.sql(MEMBERSHIP_SQL + " AND o.id = ?")
                .params(userId, organizationId)
                .query((rs, rowNum) -> new Row(
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_name"),
                        rs.getString("slug"),
                        rs.getString("organization_state"),
                        rs.getString("member_state"),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"),
                        rs.getString("role_name")))
                .list();

        return withPermissions(rows).stream().findFirst();
    }

    @Override
    public Optional<MembershipView> byRole(UUID organizationId, UUID roleId) {
        if (roleId == null) {
            return Optional.empty();
        }

        return jdbc.sql("SELECT o.name, o.slug, r.code, r.name AS role_name"
                        + " FROM organization o JOIN organization_role r ON r.organization_id = o.id"
                        + " WHERE o.id = ? AND r.id = ? AND o.state = 'active'")
                .params(organizationId, roleId)
                .query((rs, rowNum) -> new MembershipView(
                        organizationId,
                        rs.getString("name"),
                        rs.getString("slug"),
                        "active",
                        roleId,
                        rs.getString("code"),
                        rs.getString("role_name"),
                        permissionsOf(java.util.List.of(roleId)).getOrDefault(roleId, java.util.Set.of())))
                .optional();
    }

    @Override
    public Optional<UUID> preferredOrganization(UUID userId) {
        return membershipsOf(userId).stream().map(MembershipView::organizationId).findFirst();
    }

    @Override
    public Optional<OrganizationView> current() {
        UUID organizationId = OrganizationContextHolder.current().organizationId();
        if (organizationId == null) {
            return Optional.empty();
        }

        return jdbc.sql("""
                        SELECT id, name, slug, sso_domain, default_preset_code, state, created_at
                        FROM organization WHERE id = ?
                        """)
                .param(organizationId)
                .query((rs, rowNum) -> new OrganizationView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("sso_domain"),
                        rs.getString("default_preset_code"),
                        rs.getString("state"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public boolean slugAvailable(String slug) {
        return jdbc.sql("SELECT count(*) FROM organization WHERE slug = ?")
                .param(slug)
                .query(Integer.class)
                .single() == 0;
    }

    @Override
    @Transactional
    public OrganizationView create(UUID creatorId, String name, String slug, String presetCode) {
        String cleanName = required(name, "Organization name");
        String cleanSlug = slugify(slug == null || slug.isBlank() ? cleanName : slug);

        if (!cleanSlug.matches("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]")) {
            throw new IllegalArgumentException(
                    "The address may contain 3 to 40 characters: lowercase letters, digits and a hyphen");
        }
        if (!slugAvailable(cleanSlug)) {
            throw new ConflictException("The address " + cleanSlug + " is taken", "SLUG_TAKEN");
        }

        UUID organizationId = UUID.randomUUID();
        String preset = presetCode == null || presetCode.isBlank() ? "kanban" : presetCode;

        enterOrganization(organizationId);

        jdbc.sql("INSERT INTO organization (id, name, slug, default_preset_code, created_by)"
                        + " VALUES (?, ?, ?, ?, ?)")
                .params(organizationId, cleanName, cleanSlug, preset, creatorId)
                .update();

        UUID adminRoleId = seedRoles(organizationId);

        jdbc.sql("INSERT INTO organization_member"
                        + " (id, organization_id, user_id, role_id, capacity, state, last_seen_at)"
                        + " VALUES (?, ?, ?, ?, 0, 'active', now())")
                .params(UUID.randomUUID(), organizationId, creatorId, adminRoleId)
                .update();

        jdbc.sql("INSERT INTO workspace_settings (id, organization_id) VALUES (?, ?)")
                .params(UUID.randomUUID(), organizationId)
                .update();

        audit.record("organization.create", cleanSlug, Map.of("name", cleanName));

        return new OrganizationView(organizationId, cleanName, cleanSlug, null, preset, "active", Instant.now());
    }

    @Override
    @Transactional
    public OrganizationView update(PatchBody patch) {
        OrganizationContext context = OrganizationContextHolder.current();
        context.require(Permission.ORG_MANAGE);

        UUID organizationId = context.requireOrganizationId();

        if (patch.has("name")) {
            jdbc.sql("UPDATE organization SET name = ? WHERE id = ?")
                    .params(required(patch.text("name"), "Organization name"), organizationId)
                    .update();
        }
        if (patch.has("slug")) {
            String slug = slugify(required(patch.text("slug"), "Address"));
            if (!slug.matches("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]")) {
                throw new IllegalArgumentException(
                        "The address may contain 3 to 40 characters: lowercase letters, digits and a hyphen");
            }
            if (!slug.equals(currentSlug(organizationId)) && !slugAvailable(slug)) {
                throw new ConflictException("The address " + slug + " is taken", "SLUG_TAKEN");
            }
            jdbc.sql("UPDATE organization SET slug = ? WHERE id = ?").params(slug, organizationId).update();
        }
        if (patch.has("defaultPresetCode")) {
            jdbc.sql("UPDATE organization SET default_preset_code = ? WHERE id = ?")
                    .params(required(patch.text("defaultPresetCode"), "Preset"), organizationId)
                    .update();
        }
        if (patch.has("ssoDomain")) {
            String domain = patch.text("ssoDomain");
            jdbc.sql("UPDATE organization SET sso_domain = ? WHERE id = ?")
                    .params(domain == null || domain.isBlank() ? null : domain.trim().toLowerCase(), organizationId)
                    .update();
        }

        audit.record("organization.update", organizationId.toString(), Map.of());
        return current().orElseThrow(() -> NotFoundException.of("Organization", organizationId.toString()));
    }

    private String currentSlug(UUID organizationId) {
        return jdbc.sql("SELECT slug FROM organization WHERE id = ?")
                .param(organizationId)
                .query(String.class)
                .single();
    }

    private void enterOrganization(UUID organizationId) {
        jdbc.sql("SELECT set_config('app.organization_id', ?, true)")
                .param(organizationId.toString())
                .query(String.class)
                .list();
    }

    private UUID seedRoles(UUID organizationId) {
        UUID adminRoleId = null;

        for (RoleTemplates.Template template : RoleTemplates.ALL) {
            UUID roleId = UUID.randomUUID();

            jdbc.sql("INSERT INTO organization_role (id, organization_id, code, name, position, protected)"
                            + " VALUES (?, ?, ?, ?, ?, ?)")
                    .params(roleId, organizationId, template.code(), template.name(),
                            template.position(), template.isProtected())
                    .update();

            for (Permission permission : template.permissions()) {
                jdbc.sql("INSERT INTO organization_role_permission (role_id, permission) VALUES (?, ?)")
                        .params(roleId, permission.code())
                        .update();
            }

            if ("admin".equals(template.code())) {
                adminRoleId = roleId;
            }
        }

        return adminRoleId;
    }

    private static String slugify(String value) {
        String ascii = java.text.Normalizer
                .normalize(value.trim().toLowerCase().replace("ł", "l"), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    @Override
    @Transactional
    public void markSeen(UUID userId, UUID organizationId) {
        jdbc.sql("UPDATE organization_member SET last_seen_at = now() WHERE user_id = ? AND organization_id = ?")
                .params(userId, organizationId)
                .update();
    }

    private List<MembershipView> withPermissions(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, Set<Permission>> byRole = permissionsOf(rows.stream().map(Row::roleId).toList());
        List<MembershipView> views = new ArrayList<>(rows.size());

        for (Row row : rows) {
            views.add(new MembershipView(
                    row.organizationId(),
                    row.organizationName(),
                    row.slug(),
                    row.memberState(),
                    row.roleId(),
                    row.roleCode(),
                    row.roleName(),
                    byRole.getOrDefault(row.roleId(), Set.of())));
        }

        return views;
    }

    Map<UUID, Set<Permission>> permissionsOf(List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Set<Permission>> byRole = new LinkedHashMap<>();
        jdbc.sql("SELECT role_id, permission FROM organization_role_permission WHERE role_id IN (:ids)")
                .param("ids", roleIds)
                .query((rs, rowNum) -> Map.entry(
                        rs.getObject("role_id", UUID.class),
                        rs.getString("permission")))
                .list()
                .forEach(entry -> byRole
                        .computeIfAbsent(entry.getKey(), key -> EnumSet.noneOf(Permission.class))
                        .add(Permission.of(entry.getValue())));

        return byRole;
    }

    record Row(
            UUID organizationId,
            String organizationName,
            String slug,
            String organizationState,
            String memberState,
            UUID roleId,
            String roleCode,
            String roleName) {
    }
}
