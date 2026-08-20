package app.nowtask.identity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.RoleId;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class MemberService {

    private final AppUserRepository users;
    private final JdbcClient jdbc;
    private final AuditLog audit;

    MemberService(AppUserRepository users, JdbcClient jdbc, AuditLog audit) {
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public UserView invite(String name, String email, String role, Integer capacity) {
        String cleanName = required(name, "Full name");
        String cleanEmail = required(email, "Email address").toLowerCase();

        if (!cleanEmail.contains("@") || cleanEmail.startsWith("@") || cleanEmail.endsWith("@")) {
            throw new RuleViolationException("The email address is invalid");
        }
        if (users.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            throw new RuleViolationException("A person with the address " + cleanEmail + " already exists");
        }

        UUID id = UUID.randomUUID();
        RoleId roleId = RoleId.of(role == null ? "member" : role);

        jdbc.sql("""
                INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending, invited_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, true, ?)
                """)
                .params(id, cleanName, UserNames.shortNameOf(cleanName), UserNames.initialsOf(cleanName), cleanEmail,
                        roleId.code(), capacity == null ? 0 : capacity, LocalDate.now())
                .update();

        audit.record("member.invite", cleanEmail, Map.of("role", roleId.code()));
        return users.findById(id).map(UserDirectoryService::toView)
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));
    }

    public UserView update(UUID id, PatchBody patch) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));

        if (patch.has("role")) {
            RoleId role = RoleId.of(required(patch.text("role"), "Rola"));
            guardLastAdmin(user, role);
            jdbc.sql("UPDATE app_user SET role = ? WHERE id = ?").params(role.code(), id).update();
            audit.record("member.role", user.getEmail(), Map.of("role", role.code()));
        }
        if (patch.has("capacity")) {
            Integer capacity = patch.number("capacity");
            jdbc.sql("UPDATE app_user SET capacity = ? WHERE id = ?")
                    .params(capacity == null ? 0 : capacity, id).update();
        }
        if (patch.has("name")) {
            String name = required(patch.text("name"), "Full name");
            jdbc.sql("UPDATE app_user SET name = ?, short_name = ?, initials = ? WHERE id = ?")
                    .params(name, UserNames.shortNameOf(name), UserNames.initialsOf(name), id).update();
        }
        if (patch.has("pending")) {
            boolean pending = Boolean.TRUE.equals(patch.flag("pending"));
            jdbc.sql("UPDATE app_user SET pending = ? WHERE id = ?").params(pending, id).update();
            audit.record(pending ? "member.suspend" : "member.activate", user.getEmail(), Map.of());
        }

        return users.findById(id).map(UserDirectoryService::toView)
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));
    }

    public void remove(UUID id) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> NotFoundException.of("User", id.toString()));

        if (user.getRole() == RoleId.ADMIN && adminCount() <= 1) {
            throw new RuleViolationException("This is the only administrator, they cannot be removed");
        }

        jdbc.sql("UPDATE task SET assignee_id = NULL WHERE assignee_id = ?").params(id).update();
        jdbc.sql("UPDATE task SET reviewer_id = NULL WHERE reviewer_id = ?").params(id).update();
        jdbc.sql("UPDATE subtask SET assignee_id = NULL WHERE assignee_id = ?").params(id).update();
        jdbc.sql("DELETE FROM task_watcher WHERE user_id = ?").params(id).update();
        jdbc.sql("DELETE FROM team_member WHERE user_id = ?").params(id).update();
        jdbc.sql("DELETE FROM app_user WHERE id = ?").params(id).update();

        refreshHeadcounts();
        audit.record("member.remove", user.getEmail(), Map.of());
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
            throw NotFoundException.of("Zespol", id.toString());
        }
        audit.record("team.rename", cleanName, Map.of());
        return team(id);
    }

    public void deleteTeam(UUID id) {
        jdbc.sql("DELETE FROM team_member WHERE team_id = ?").params(id).update();
        int deleted = jdbc.sql("DELETE FROM team WHERE id = ?").params(id).update();
        if (deleted == 0) {
            throw NotFoundException.of("Zespol", id.toString());
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
        if (users.findById(userId).isEmpty()) {
            throw NotFoundException.of("User", userId.toString());
        }
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

    private TeamView team(UUID id) {
        return jdbc.sql("SELECT id, name, headcount FROM team WHERE id = ?")
                .params(id)
                .query((rs, rowNum) -> new TeamView(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("headcount")))
                .optional()
                .orElseThrow(() -> NotFoundException.of("Zespol", id.toString()));
    }

    private void refreshHeadcounts() {
        jdbc.sql("UPDATE team SET headcount = "
                + "(SELECT count(*) FROM team_member WHERE team_member.team_id = team.id)").update();
    }

    private void guardLastAdmin(AppUser user, RoleId nextRole) {
        if (user.getRole() == RoleId.ADMIN && nextRole != RoleId.ADMIN && adminCount() <= 1) {
            throw new RuleViolationException("This is the only administrator, leave them that role");
        }
    }

    private int adminCount() {
        Integer count = jdbc.sql("SELECT count(*) FROM app_user WHERE role = 'admin' AND pending = false")
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new RuleViolationException(label + " is required");
        }
        return value.trim();
    }
}
