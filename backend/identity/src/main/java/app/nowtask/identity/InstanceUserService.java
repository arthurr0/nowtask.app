package app.nowtask.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.InstanceViews.Page;
import app.nowtask.identity.api.InstanceViews.UserItem;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.events.RealtimeEvents;

@Service
@Transactional
public class InstanceUserService {

    private final JdbcClient jdbc;
    private final SessionService sessions;
    private final InstanceAuditService audit;
    private final ApplicationEventPublisher events;

    InstanceUserService(
            JdbcClient jdbc, SessionService sessions, InstanceAuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<UserItem> list(String query, String state, Boolean admin, int page, int size) {
        if (state != null && !state.isBlank() && !"active".equals(state) && !"disabled".equals(state)) {
            throw new IllegalArgumentException("The state has to be active or disabled");
        }

        int pageNumber = InstancePaging.page(page);
        int pageSize = InstancePaging.size(size);

        Rows rows = fetch(null, InstancePaging.pattern(query), state == null || state.isBlank() ? null : state,
                admin, pageSize, pageNumber * pageSize);

        return new Page<>(rows.items(), rows.total(), pageNumber, pageSize);
    }

    public void disable(UUID actorId, UUID id) {
        if (actorId.equals(id)) {
            throw new ConflictException("You cannot disable your own account", "SELF_ACTION");
        }

        UserItem user = require(id);

        if ("disabled".equals(user.state())) {
            return;
        }

        List<UUID> activeAdmins = lockActiveAdmins();
        if (activeAdmins.contains(id) && activeAdmins.size() <= 1) {
            throw new ConflictException(
                    "At least one instance administrator has to remain", "LAST_INSTANCE_ADMIN");
        }

        jdbc.sql("UPDATE app_user SET state = 'disabled' WHERE id = ? AND state = 'active'").param(id).update();
        int closed = closeSessions(id, user.email());

        audit.record(actorId, "user.disable", "user", id.toString(), user.email(), Map.of("sessions", closed));

        events.publishEvent(new RealtimeEvents.UserAccessRevoked(id));
    }

    public void enable(UUID actorId, UUID id) {
        UserItem user = require(id);

        if ("active".equals(user.state())) {
            return;
        }

        jdbc.sql("UPDATE app_user SET state = 'active' WHERE id = ? AND state = 'disabled'").param(id).update();

        audit.record(actorId, "user.enable", "user", id.toString(), user.email(), Map.of());
    }

    public void revokeSessions(UUID actorId, UUID id) {
        UserItem user = require(id);

        int closed = closeSessions(id, user.email());

        audit.record(actorId, "user.sessions_revoke", "user", id.toString(), user.email(), Map.of("sessions", closed));

        events.publishEvent(new RealtimeEvents.UserAccessRevoked(id));
    }

    public UserItem setInstanceAdmin(UUID actorId, UUID id, boolean value) {
        UserItem user = require(id);

        if (value) {
            if (!user.instanceAdmin()) {
                jdbc.sql("UPDATE app_user SET instance_admin = TRUE WHERE id = ?").param(id).update();
                audit.record(actorId, "user.instance_admin_grant", "user", id.toString(), user.email(),
                        Map.of("source", "panel"));
            }
            return require(id);
        }

        if (user.instanceAdmin()) {
            List<UUID> activeAdmins = lockActiveAdmins();

            if ("active".equals(user.state()) && activeAdmins.size() <= 1) {
                throw new ConflictException(
                        "At least one instance administrator has to remain", "LAST_INSTANCE_ADMIN");
            }
            if (actorId.equals(id)) {
                throw new ConflictException("You cannot revoke your own administrator role", "SELF_ACTION");
            }

            jdbc.sql("UPDATE app_user SET instance_admin = FALSE WHERE id = ?").param(id).update();
            audit.record(actorId, "user.instance_admin_revoke", "user", id.toString(), user.email(),
                    new LinkedHashMap<>());
        }

        return require(id);
    }

    @Transactional(readOnly = true)
    public boolean isLastActiveAdmin(UUID userId) {
        List<UUID> activeAdmins = jdbc.sql("SELECT id FROM app_user WHERE instance_admin AND state = 'active'")
                .query(UUID.class)
                .list();

        return activeAdmins.size() == 1 && activeAdmins.get(0).equals(userId);
    }

    private List<UUID> lockActiveAdmins() {
        return jdbc.sql("SELECT id FROM app_user WHERE instance_admin AND state = 'active' ORDER BY id FOR UPDATE")
                .query(UUID.class)
                .list();
    }

    private int closeSessions(UUID id, String email) {
        int closed = sessions.dropAll(id);
        jdbc.sql("DELETE FROM spring_session WHERE principal_name = ?").param(email).update();
        return closed;
    }

    private UserItem require(UUID id) {
        return fetch(id, null, null, null, 1, 0).items().stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("User", id));
    }

    private Rows fetch(UUID id, String pattern, String state, Boolean admin, int limit, int offset) {
        List<UserItem> items = new ArrayList<>();
        long[] total = {0};

        jdbc.sql("""
                        SELECT * FROM instance_user_page(
                            CAST(:id AS UUID), CAST(:pattern AS TEXT), CAST(:state AS TEXT),
                            CAST(:admin AS BOOLEAN), :limit, :offset)
                        """)
                .param("id", id)
                .param("pattern", pattern)
                .param("state", state)
                .param("admin", admin)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    total[0] = rs.getLong("total");
                    UUID userId = rs.getObject("id", UUID.class);
                    if (userId != null) {
                        items.add(map(rs, userId));
                    }
                    return userId;
                })
                .list();

        return new Rows(items, total[0]);
    }

    private static UserItem map(ResultSet rs, UUID id) throws SQLException {
        return new UserItem(
                id,
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("initials"),
                rs.getString("state"),
                rs.getBoolean("instance_admin"),
                rs.getBoolean("email_verified"),
                rs.getLong("organization_count"),
                instant(rs.getTimestamp("last_seen_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record Rows(List<UserItem> items, long total) {
    }
}
