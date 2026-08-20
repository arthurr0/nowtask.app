package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.AuditView;
import app.nowtask.identity.api.Audits;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class AuditService implements AuditLog, Audits {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final ObjectMapper json;

    AuditService(JdbcClient jdbc, AppUserRepository users, ObjectMapper json) {
        this.jdbc = jdbc;
        this.users = users;
        this.json = json;
    }

    @Override
    public void record(String action, String subject, Map<String, Object> detail) {
        Actor actor = currentActor();
        try {
            jdbc.sql("""
                    INSERT INTO audit_event (id, at, actor_id, api_key_id, actor_label, action, subject, detail)
                    VALUES (:id, :at, :actorId, :apiKeyId, :actorLabel, :action, :subject, CAST(:detail AS JSONB))
                    """)
                    .param("id", UUID.randomUUID())
                    .param("at", Timestamp.from(Instant.now()))
                    .param("actorId", actor.userId())
                    .param("apiKeyId", actor.apiKeyId())
                    .param("actorLabel", actor.label())
                    .param("action", action)
                    .param("subject", subject == null ? "" : subject)
                    .param("detail", write(detail == null ? Map.of() : detail))
                    .update();
        } catch (RuntimeException e) {
            log.warn("Failed to write the audit entry {} {}", action, subject, e);
        }
    }

    @Override
    public Page page(int page, int size) {
        int limit = size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);
        int offset = Math.max(page, 0) * limit;

        int total = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        List<AuditView> items = jdbc.sql("""
                SELECT id, at, actor_id, api_key_id, actor_label, action, subject, detail
                FROM audit_event ORDER BY at DESC, id DESC LIMIT :limit OFFSET :offset
                """)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new AuditView(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("at").toInstant(),
                        rs.getObject("actor_id", UUID.class),
                        rs.getObject("api_key_id", UUID.class),
                        rs.getString("actor_label"),
                        rs.getString("action"),
                        rs.getString("subject"),
                        read(rs.getString("detail"))))
                .list();

        return new Page(items, total);
    }

    @Override
    public List<AuditView> agentActivity(int limit) {
        int rows = limit <= 0 ? 20 : Math.min(limit, MAX_PAGE_SIZE);
        return jdbc.sql("""
                SELECT id, at, actor_id, api_key_id, actor_label, action, subject, detail
                FROM audit_event WHERE api_key_id IS NOT NULL ORDER BY at DESC, id DESC LIMIT :limit
                """)
                .param("limit", rows)
                .query((rs, rowNum) -> new AuditView(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("at").toInstant(),
                        rs.getObject("actor_id", UUID.class),
                        rs.getObject("api_key_id", UUID.class),
                        rs.getString("actor_label"),
                        rs.getString("action"),
                        rs.getString("subject"),
                        read(rs.getString("detail"))))
                .list();
    }

    private record Actor(UUID userId, UUID apiKeyId, String label) {
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new Actor(null, null, "anonymous");
        }
        if (authentication.getPrincipal() instanceof ApiKeyIdentity identity) {
            return new Actor(identity.ownerId(), identity.keyId(), identity.agentLabel());
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .map(user -> new Actor(user.getId(), null, user.getName()))
                .orElseGet(() -> new Actor(null, null, authentication.getName()));
    }

    private String write(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private Map<String, Object> read(String stored) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(stored, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            return Map.of();
        }
    }
}
