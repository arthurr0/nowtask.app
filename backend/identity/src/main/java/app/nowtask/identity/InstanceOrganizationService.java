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
import app.nowtask.identity.api.InstanceViews.OrganizationItem;
import app.nowtask.identity.api.InstanceViews.Page;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.events.RealtimeEvents;

@Service
@Transactional
public class InstanceOrganizationService {

    private final JdbcClient jdbc;
    private final InstanceAuditService audit;
    private final ApplicationEventPublisher events;

    InstanceOrganizationService(JdbcClient jdbc, InstanceAuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationItem> list(String query, String state, int page, int size) {
        if (state != null && !state.isBlank() && !"active".equals(state) && !"suspended".equals(state)) {
            throw new IllegalArgumentException("The state has to be active or suspended");
        }

        int pageNumber = InstancePaging.page(page);
        int pageSize = InstancePaging.size(size);

        Rows rows = fetch(null, InstancePaging.pattern(query), state == null || state.isBlank() ? null : state,
                pageSize, pageNumber * pageSize);

        return new Page<>(rows.items(), rows.total(), pageNumber, pageSize);
    }

    public void suspend(UUID actorId, UUID id) {
        OrganizationItem organization = require(id);

        if ("suspended".equals(organization.state())) {
            return;
        }

        jdbc.sql("SELECT instance_organization_set_state(:id, 'suspended')")
                .param("id", id)
                .query(Integer.class)
                .single();

        audit.record(actorId, "organization.suspend", "organization", id.toString(), organization.name(),
                Map.of("slug", organization.slug()));

        events.publishEvent(new RealtimeEvents.OrganizationAccessRevoked(id));
    }

    public void restore(UUID actorId, UUID id) {
        OrganizationItem organization = require(id);

        if ("active".equals(organization.state())) {
            return;
        }

        jdbc.sql("SELECT instance_organization_set_state(:id, 'active')")
                .param("id", id)
                .query(Integer.class)
                .single();

        audit.record(actorId, "organization.restore", "organization", id.toString(), organization.name(),
                Map.of("slug", organization.slug()));
    }

    public void delete(UUID actorId, UUID id, String confirmSlug) {
        OrganizationItem organization = require(id);

        if (confirmSlug == null || !organization.slug().equals(confirmSlug.trim())) {
            throw new RuleViolationException(
                    "The confirmation does not match the organization address", "SLUG_MISMATCH");
        }

        jdbc.sql("SELECT instance_organization_close(:id)")
                .param("id", id)
                .query(Integer.class)
                .single();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("slug", organization.slug());
        detail.put("members", organization.memberCount());
        detail.put("tasks", organization.taskCount());

        audit.record(actorId, "organization.delete", "organization", id.toString(), organization.name(), detail);

        events.publishEvent(new RealtimeEvents.OrganizationAccessRevoked(id));
    }

    private OrganizationItem require(UUID id) {
        return fetch(id, null, null, 1, 0).items().stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Organization", id));
    }

    private Rows fetch(UUID id, String pattern, String state, int limit, int offset) {
        List<OrganizationItem> items = new ArrayList<>();
        long[] total = {0};

        jdbc.sql("""
                        SELECT * FROM instance_organization_page(
                            CAST(:id AS UUID), CAST(:pattern AS TEXT), CAST(:state AS TEXT), :limit, :offset)
                        """)
                .param("id", id)
                .param("pattern", pattern)
                .param("state", state)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    total[0] = rs.getLong("total");
                    UUID organizationId = rs.getObject("id", UUID.class);
                    if (organizationId != null) {
                        items.add(map(rs, organizationId));
                    }
                    return organizationId;
                })
                .list();

        return new Rows(items, total[0]);
    }

    private static OrganizationItem map(ResultSet rs, UUID id) throws SQLException {
        return new OrganizationItem(
                id,
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("state"),
                rs.getString("sso_domain"),
                rs.getLong("member_count"),
                rs.getLong("task_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("suspended_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record Rows(List<OrganizationItem> items, long total) {
    }
}
