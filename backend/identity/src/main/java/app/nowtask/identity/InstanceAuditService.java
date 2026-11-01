package app.nowtask.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.InstanceViews.AuditItem;
import app.nowtask.identity.api.InstanceViews.Page;
import app.nowtask.identity.api.InstanceViews.PersonRef;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class InstanceAuditService {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    InstanceAuditService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(
            UUID actorId,
            String action,
            String targetType,
            String targetId,
            String targetLabel,
            Map<String, Object> detail) {
        jdbc.sql("""
                INSERT INTO instance_audit_event (id, actor_id, action, target_type, target_id, target_label, detail)
                VALUES (:id, :actorId, :action, :targetType, :targetId, :targetLabel, CAST(:detail AS JSONB))
                """)
                .param("id", UUID.randomUUID())
                .param("actorId", actorId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("targetLabel", targetLabel)
                .param("detail", write(detail == null ? Map.of() : detail))
                .update();
    }

    @Transactional(readOnly = true)
    public Page<AuditItem> page(int page, int size) {
        int pageNumber = InstancePaging.page(page);
        int pageSize = InstancePaging.size(size);

        long total = jdbc.sql("SELECT count(*) FROM instance_audit_event").query(Long.class).single();

        List<AuditItem> items = jdbc.sql("""
                        SELECT e.id, e.actor_id, u.name AS actor_name, e.action, e.target_type, e.target_id,
                               e.target_label, e.detail, e.created_at
                        FROM instance_audit_event e
                        LEFT JOIN app_user u ON u.id = e.actor_id
                        ORDER BY e.created_at DESC, e.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", pageSize)
                .param("offset", pageNumber * pageSize)
                .query((rs, rowNum) -> new AuditItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("actor_id", UUID.class) == null
                                ? null
                                : new PersonRef(rs.getObject("actor_id", UUID.class), rs.getString("actor_name")),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("target_label"),
                        read(rs.getString("detail")),
                        rs.getTimestamp("created_at").toInstant()))
                .list();

        return new Page<>(items, total, pageNumber, pageSize);
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
