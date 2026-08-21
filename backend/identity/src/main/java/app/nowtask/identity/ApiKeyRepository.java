package app.nowtask.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ApiKeyRepository {
    private static final String COLUMNS = "id, organization_id, role_id, prefix, label, token_hash,"
            + " scopes, owner_id, last_used_at, created_at, expires_at, revoked_at";

    private final JdbcClient jdbc;

    ApiKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ApiKeyRow> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM api_key ORDER BY created_at")
                .query(ApiKeyRepository::map)
                .list();
    }

    Optional<ApiKeyRow> findByPrefix(String prefix) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM api_key_lookup(:prefix)")
                .param("prefix", prefix)
                .query(ApiKeyRepository::map)
                .optional();
    }

    Optional<ApiKeyRow> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM api_key WHERE id = :id")
                .param("id", id)
                .query(ApiKeyRepository::map)
                .optional();
    }

    void insert(ApiKeyRow row, UUID createdBy) {
        jdbc.sql("""
                INSERT INTO api_key (id, organization_id, role_id, prefix, label, token_hash, scopes,
                                     owner_id, created_by, created_at, expires_at)
                VALUES (:id, :organizationId, :roleId, :prefix, :label, :tokenHash, :scopes,
                        :ownerId, :createdBy, :createdAt, :expiresAt)
                """)
                .param("id", row.id())
                .param("organizationId", row.organizationId())
                .param("roleId", row.roleId())
                .param("prefix", row.prefix())
                .param("label", row.label())
                .param("tokenHash", row.tokenHash())
                .param("scopes", row.scopes())
                .param("ownerId", row.ownerId())
                .param("createdBy", createdBy)
                .param("createdAt", Timestamp.from(row.createdAt()))
                .param("expiresAt", row.expiresAt() == null ? null : Timestamp.from(row.expiresAt()))
                .update();
    }

    int revoke(UUID id, Instant at) {
        return jdbc.sql("UPDATE api_key SET revoked_at = :at WHERE id = :id AND revoked_at IS NULL")
                .param("at", Timestamp.from(at))
                .param("id", id)
                .update();
    }

    void touch(UUID id, Instant at) {
        jdbc.sql("SELECT api_key_touch(:id, :at)")
                .param("id", id)
                .param("at", Timestamp.from(at))
                .query(String.class)
                .list();
    }

    private static ApiKeyRow map(ResultSet rs, int rowNum) throws SQLException {
        return new ApiKeyRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("role_id", UUID.class),
                rs.getString("prefix"),
                rs.getString("label"),
                rs.getString("token_hash"),
                rs.getString("scopes"),
                rs.getObject("owner_id", UUID.class),
                instant(rs, "last_used_at"),
                instant(rs, "created_at"),
                instant(rs, "expires_at"),
                instant(rs, "revoked_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
