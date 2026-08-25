package app.nowtask.integrations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import app.nowtask.integrations.GitHubRows.Installation;
import app.nowtask.integrations.GitHubRows.Link;

@Repository
class GitHubStore {

    private static final String LINK_COLUMNS = "id, task_key, repo_full_name, kind, number, ref, node_id,"
            + " url, state, title, origin, author_login, detail, check_state, synced_title, synced_body,"
            + " updated_at";

    private final JdbcClient jdbc;

    GitHubStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Installation> lookup(long installationId) {
        return jdbc.sql("SELECT id, organization_id, installation_id, account_login, account_type, suspended,"
                        + " connected_by FROM github_installation_lookup(:installation)")
                .param("installation", installationId)
                .query(GitHubStore::installation)
                .optional();
    }

    void release(long installationId, Instant at) {
        jdbc.sql("SELECT github_installation_release(:installation, :at)")
                .param("installation", installationId)
                .param("at", Timestamp.from(at))
                .query(String.class)
                .list();
    }

    Optional<Installation> current() {
        return jdbc.sql("SELECT id, organization_id, installation_id, account_login, account_type, suspended,"
                        + " connected_by FROM github_installation"
                        + " WHERE removed_at IS NULL ORDER BY created_at DESC LIMIT 1")
                .query(GitHubStore::installation)
                .optional();
    }

    void connect(long installationId, String accountLogin, String accountType, UUID connectedBy) {
        jdbc.sql("""
                INSERT INTO github_installation (id, installation_id, account_login, account_type, connected_by)
                VALUES (:id, :installation, :login, :type, :connectedBy)
                """)
                .param("id", UUID.randomUUID())
                .param("installation", installationId)
                .param("login", accountLogin == null ? "" : accountLogin)
                .param("type", accountType == null ? "" : accountType)
                .param("connectedBy", connectedBy)
                .update();
    }

    void suspend(long installationId, boolean suspended) {
        jdbc.sql("UPDATE github_installation SET suspended = :suspended WHERE installation_id = :installation")
                .param("suspended", suspended)
                .param("installation", installationId)
                .update();
    }

    boolean firstDelivery(String deliveryId, String event) {
        return jdbc.sql("""
                INSERT INTO github_delivery (id, delivery_id, event)
                VALUES (:id, :delivery, :event)
                ON CONFLICT (organization_id, delivery_id) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("delivery", deliveryId)
                .param("event", event)
                .update() == 1;
    }

    int pruneDeliveries(int olderThanDays) {
        return jdbc.sql("SELECT github_deliveries_prune(:days)")
                .param("days", olderThanDays)
                .query(Integer.class)
                .single();
    }

    Optional<Link> linkByTarget(String repo, String kind, int number) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM github_link"
                        + " WHERE repo_full_name = :repo AND kind = :kind AND ref = :ref")
                .param("repo", repo)
                .param("kind", kind)
                .param("ref", String.valueOf(number))
                .query(GitHubStore::link)
                .optional();
    }

    List<Link> linksByTask(String taskKey) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM github_link"
                        + " WHERE task_key = :taskKey ORDER BY created_at DESC")
                .param("taskKey", taskKey)
                .query(GitHubStore::link)
                .list();
    }

    Optional<Link> issueLink(String taskKey) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM github_link"
                        + " WHERE task_key = :taskKey AND kind = 'issue' ORDER BY created_at LIMIT 1")
                .param("taskKey", taskKey)
                .query(GitHubStore::link)
                .optional();
    }

    void saveLink(Link row) {
        jdbc.sql("""
                INSERT INTO github_link (id, task_key, repo_full_name, kind, number, ref, node_id, url, state,
                                         title, origin, author_login, detail, check_state,
                                         synced_title, synced_body)
                VALUES (:id, :taskKey, :repo, :kind, :number, :ref, :nodeId, :url, :state,
                        :title, :origin, :authorLogin, :detail, :checkState, :syncedTitle, :syncedBody)
                ON CONFLICT (organization_id, repo_full_name, kind, ref) DO UPDATE
                SET task_key = EXCLUDED.task_key,
                    number = EXCLUDED.number,
                    node_id = EXCLUDED.node_id,
                    url = EXCLUDED.url,
                    state = EXCLUDED.state,
                    title = EXCLUDED.title,
                    author_login = EXCLUDED.author_login,
                    detail = EXCLUDED.detail,
                    check_state = EXCLUDED.check_state,
                    synced_title = EXCLUDED.synced_title,
                    synced_body = EXCLUDED.synced_body,
                    updated_at = now()
                """)
                .param("id", row.id() == null ? UUID.randomUUID() : row.id())
                .param("taskKey", row.taskKey())
                .param("repo", row.repo())
                .param("kind", row.kind())
                .param("number", row.number())
                .param("ref", row.ref() == null ? "" : row.ref())
                .param("nodeId", blank(row.nodeId()))
                .param("url", blank(row.url()))
                .param("state", blank(row.state()))
                .param("title", blank(row.title()))
                .param("origin", row.origin() == null ? "github" : row.origin())
                .param("authorLogin", blank(row.authorLogin()))
                .param("detail", blank(row.detail()))
                .param("checkState", blank(row.checkState()))
                .param("syncedTitle", blank(row.syncedTitle()))
                .param("syncedBody", blank(row.syncedBody()))
                .update();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    void markSynced(UUID id, String title, String body, String state) {
        jdbc.sql("""
                UPDATE github_link
                SET synced_title = :title, synced_body = :body, state = :state, title = :title, updated_at = now()
                WHERE id = :id
                """)
                .param("title", title == null ? "" : title)
                .param("body", body == null ? "" : body)
                .param("state", state == null ? "" : state)
                .param("id", id)
                .update();
    }

    void deleteLinksForTask(String taskKey) {
        jdbc.sql("DELETE FROM github_link WHERE task_key = :taskKey")
                .param("taskKey", taskKey)
                .update();
    }

    private static Installation installation(ResultSet rs, int rowNum) throws SQLException {
        return new Installation(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getLong("installation_id"),
                rs.getString("account_login"),
                rs.getString("account_type"),
                rs.getBoolean("suspended"),
                rs.getObject("connected_by", UUID.class));
    }

    private static Link link(ResultSet rs, int rowNum) throws SQLException {
        return new Link(
                rs.getObject("id", UUID.class),
                rs.getString("task_key"),
                rs.getString("repo_full_name"),
                rs.getString("kind"),
                rs.getObject("number", Integer.class),
                rs.getString("ref"),
                rs.getString("node_id"),
                rs.getString("url"),
                rs.getString("state"),
                rs.getString("title"),
                rs.getString("origin"),
                rs.getString("author_login"),
                rs.getString("detail"),
                rs.getString("check_state"),
                rs.getString("synced_title"),
                rs.getString("synced_body"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
    }
}
