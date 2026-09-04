package app.nowtask.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.shared.NotFoundException;

@Service
class MembershipExitService {

    private final JdbcClient jdbc;
    private final RoleService roles;
    private final AuditLog audit;

    MembershipExitService(JdbcClient jdbc, RoleService roles, AuditLog audit) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void leave(UUID userId, UUID organizationId, String email) {
        boolean member = jdbc.sql("""
                        SELECT count(*) FROM organization_member
                        WHERE organization_id = ? AND user_id = ?
                        """)
                .params(organizationId, userId)
                .query(Integer.class)
                .single() > 0;

        if (!member) {
            throw NotFoundException.of("Member", userId.toString());
        }

        boolean sole = jdbc.sql("""
                        SELECT count(*) FROM organization_member
                        WHERE organization_id = ? AND state = 'active' AND user_id <> ?
                        """)
                .params(organizationId, userId)
                .query(Integer.class)
                .single() == 0;

        jdbc.sql("UPDATE task SET assignee_id = NULL WHERE assignee_id = ?").param(userId).update();
        jdbc.sql("UPDATE task SET reviewer_id = NULL WHERE reviewer_id = ?").param(userId).update();
        jdbc.sql("UPDATE subtask SET assignee_id = NULL WHERE assignee_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM task_watcher WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM team_member WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM notification WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM user_nav_item WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM onboarding_progress WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM api_key WHERE owner_id = ?").param(userId).update();

        if (sole) {
            jdbc.sql("UPDATE organization SET state = 'deleted', deleted_at = now() WHERE id = ?")
                    .param(organizationId)
                    .update();
        }

        jdbc.sql("DELETE FROM organization_member WHERE organization_id = ? AND user_id = ?")
                .params(organizationId, userId)
                .update();

        if (!sole) {
            roles.guardTheKeysStayInside();
        }

        jdbc.sql("UPDATE team SET headcount ="
                        + " (SELECT count(*) FROM team_member WHERE team_member.team_id = team.id)")
                .update();

        audit.record(sole ? "organization.close" : "member.leave", email,
                Map.of("organization", organizationId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void close(UUID organizationId, String email) {
        jdbc.sql("DELETE FROM api_key WHERE organization_id = ?").param(organizationId).update();
        jdbc.sql("UPDATE organization SET state = 'deleted', deleted_at = now() WHERE id = ?")
                .param(organizationId)
                .update();

        audit.record("organization.close", email, Map.of("organization", organizationId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void anonymize(UUID userId) {
        jdbc.sql("DELETE FROM email_verification WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM password_reset WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM email_change WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM user_notification_pref WHERE user_id = ?").param(userId).update();
        jdbc.sql("DELETE FROM user_session WHERE user_id = ?").param(userId).update();

        jdbc.sql("""
                        UPDATE app_user
                        SET name = 'Deleted account',
                            short_name = 'Deleted',
                            initials = '--',
                            email = ?,
                            password_hash = NULL,
                            oidc_subject = NULL,
                            email_verified_at = NULL,
                            state = 'deleted'
                        WHERE id = ?
                        """)
                .params("deleted+" + userId + "@nowtask.invalid", userId)
                .update();
    }
}
