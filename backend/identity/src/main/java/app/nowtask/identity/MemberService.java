package app.nowtask.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.InviteView;
import app.nowtask.identity.api.RoleRefView;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class MemberService {

    private final AppUserRepository users;
    private final JdbcClient jdbc;
    private final AuditLog audit;
    private final InviteService invites;
    private final RoleService roles;

    MemberService(
            AppUserRepository users,
            JdbcClient jdbc,
            AuditLog audit,
            InviteService invites,
            RoleService roles) {
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
        this.invites = invites;
        this.roles = roles;
    }

    public UserView invite(String name, String email, String role, Integer capacity) {
        return asPendingUser(invites.invite(email, role));
    }

    public UserView update(UUID id, PatchBody patch) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();

        if (isInvite(organizationId, id)) {
            if (patch.has("role")) {
                throw new RuleViolationException(
                        "The role of an open invitation cannot be changed, revoke it and invite again",
                        "INVITE_IMMUTABLE");
            }
            return asPendingUser(invites.resend(id));
        }

        Membership membership = requireMembership(organizationId, id);

        if (patch.has("role")) {
            String code = required(patch.text("role"), "Role");
            UUID roleId = roles.roleIdByCode(organizationId, code);

            jdbc.sql("UPDATE organization_member SET role_id = ? WHERE organization_id = ? AND user_id = ?")
                    .params(roleId, organizationId, id)
                    .update();

            roles.guardTheKeysStayInside();
            audit.record("member.role", membership.email(), Map.of("role", code));
        }
        if (patch.has("capacity")) {
            Integer capacity = patch.number("capacity");
            jdbc.sql("UPDATE organization_member SET capacity = ? WHERE organization_id = ? AND user_id = ?")
                    .params(capacity == null ? 0 : capacity, organizationId, id)
                    .update();
        }
        if (patch.has("name")) {
            String name = required(patch.text("name"), "Full name");
            jdbc.sql("UPDATE app_user SET name = ?, short_name = ?, initials = ? WHERE id = ?")
                    .params(name, UserNames.shortNameOf(name), UserNames.initialsOf(name), id)
                    .update();
        }
        if (patch.has("pending")) {
            boolean disabled = Boolean.TRUE.equals(patch.flag("pending"));

            jdbc.sql("UPDATE organization_member SET state = ? WHERE organization_id = ? AND user_id = ?")
                    .params(disabled ? "disabled" : "active", organizationId, id)
                    .update();

            roles.guardTheKeysStayInside();
            audit.record(disabled ? "member.suspend" : "member.activate", membership.email(), Map.of());
        }

        return requireMember(organizationId, id);
    }

    public void remove(UUID id) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();

        if (isInvite(organizationId, id)) {
            invites.revoke(id);
            return;
        }

        Membership membership = requireMembership(organizationId, id);

        jdbc.sql("UPDATE task SET assignee_id = NULL WHERE assignee_id = ?").params(id).update();
        jdbc.sql("UPDATE task SET reviewer_id = NULL WHERE reviewer_id = ?").params(id).update();
        jdbc.sql("UPDATE subtask SET assignee_id = NULL WHERE assignee_id = ?").params(id).update();
        jdbc.sql("DELETE FROM task_watcher WHERE user_id = ?").params(id).update();
        jdbc.sql("DELETE FROM team_member WHERE user_id = ?").params(id).update();
        jdbc.sql("DELETE FROM organization_member WHERE organization_id = ? AND user_id = ?")
                .params(organizationId, id)
                .update();

        roles.guardTheKeysStayInside();
        refreshHeadcounts();
        audit.record("member.remove", membership.email(), Map.of());
    }

    public TeamView createTeam(String name) {
        String cleanName = required(name, "Team name");
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO team (id, name, headcount) VALUES (?, ?, 0)")
                .params(id, cleanName).update();
        audit.record("team.create", cleanName, Map.of());
        return new TeamView(id, cleanName, 0);
    }

    public TeamView renameTeam(UUID id, String name) {
        String cleanName = required(name, "Team name");
        int updated = jdbc.sql("UPDATE team SET name = ? WHERE id = ?").params(cleanName, id).update();
        if (updated == 0) {
            throw NotFoundException.of("Team", id.toString());
        }
        audit.record("team.rename", cleanName, Map.of());
        return team(id);
    }

    public void deleteTeam(UUID id) {
        jdbc.sql("DELETE FROM team_member WHERE team_id = ?").params(id).update();
        int deleted = jdbc.sql("DELETE FROM team WHERE id = ?").params(id).update();
        if (deleted == 0) {
            throw NotFoundException.of("Team", id.toString());
        }
        audit.record("team.delete", id.toString(), Map.of());
    }

    @Transactional(readOnly = true)
    public List<UUID> teamMembers(UUID teamId) {
        return jdbc.sql("SELECT user_id FROM team_member WHERE team_id = ?")
                .params(teamId)
                .query(UUID.class)
                .list();
    }

    public TeamView addMember(UUID teamId, UUID userId) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        requireMembership(organizationId, userId);

        jdbc.sql("INSERT INTO team_member (team_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
                .params(teamId, userId).update();
        refreshHeadcounts();
        return team(teamId);
    }

    public TeamView removeMember(UUID teamId, UUID userId) {
        jdbc.sql("DELETE FROM team_member WHERE team_id = ? AND user_id = ?")
                .params(teamId, userId).update();
        refreshHeadcounts();
        return team(teamId);
    }

    private UserView asPendingUser(InviteView invite) {
        return new UserView(
                invite.id(),
                invite.email(),
                invite.email(),
                UserNames.initialsOf(invite.email()),
                invite.email(),
                new RoleRefView(invite.roleId(), invite.roleCode(), invite.roleName()),
                0,
                true,
                invite.createdAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                false);
    }

    private boolean isInvite(UUID organizationId, UUID id) {
        return jdbc.sql("SELECT count(*) FROM organization_invite WHERE organization_id = ? AND id = ?")
                .params(organizationId, id)
                .query(Integer.class)
                .single() > 0;
    }

    private Membership requireMembership(UUID organizationId, UUID userId) {
        return jdbc.sql("""
                        SELECT u.email, m.state
                        FROM organization_member m
                        JOIN app_user u ON u.id = m.user_id
                        WHERE m.organization_id = ? AND m.user_id = ?
                        """)
                .params(organizationId, userId)
                .query((rs, rowNum) -> new Membership(rs.getString("email"), rs.getString("state")))
                .optional()
                .orElseThrow(() -> NotFoundException.of("Member", userId.toString()));
    }

    private UserView requireMember(UUID organizationId, UUID userId) {
        return users.findById(userId)
                .map(user -> UserDirectoryService.toView(user, roleOf(organizationId, userId)))
                .map(view -> new UserView(
                        view.id(), view.name(), view.shortName(), view.initials(), view.email(), view.role(),
                        capacityOf(organizationId, userId), disabled(organizationId, userId),
                        view.invitedOn(), view.emailVerified()))
                .orElseThrow(() -> NotFoundException.of("Member", userId.toString()));
    }

    private RoleRefView roleOf(UUID organizationId, UUID userId) {
        return jdbc.sql("""
                        SELECT r.id, r.code, r.name
                        FROM organization_member m
                        JOIN organization_role r ON r.id = m.role_id
                        WHERE m.organization_id = ? AND m.user_id = ?
                        """)
                .params(organizationId, userId)
                .query((rs, rowNum) -> new RoleRefView(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")))
                .optional()
                .orElse(null);
    }

    private int capacityOf(UUID organizationId, UUID userId) {
        Integer capacity = jdbc.sql(
                        "SELECT capacity FROM organization_member WHERE organization_id = ? AND user_id = ?")
                .params(organizationId, userId)
                .query(Integer.class)
                .optional()
                .orElse(0);
        return capacity == null ? 0 : capacity;
    }

    private boolean disabled(UUID organizationId, UUID userId) {
        return jdbc.sql("SELECT state FROM organization_member WHERE organization_id = ? AND user_id = ?")
                .params(organizationId, userId)
                .query(String.class)
                .optional()
                .map("disabled"::equals)
                .orElse(false);
    }

    private TeamView team(UUID id) {
        return jdbc.sql("SELECT id, name, headcount FROM team WHERE id = ?")
                .params(id)
                .query((rs, rowNum) -> new TeamView(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("headcount")))
                .optional()
                .orElseThrow(() -> NotFoundException.of("Team", id.toString()));
    }

    private void refreshHeadcounts() {
        jdbc.sql("UPDATE team SET headcount = "
                + "(SELECT count(*) FROM team_member WHERE team_member.team_id = team.id)").update();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new RuleViolationException(label + " is required");
        }
        return value.trim();
    }

    private record Membership(String email, String state) {
    }
}
