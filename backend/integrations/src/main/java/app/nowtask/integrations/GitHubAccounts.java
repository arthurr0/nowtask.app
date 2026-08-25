package app.nowtask.integrations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class GitHubAccounts {

    record Row(UUID userId, long githubUserId, String login, String avatarUrl) {
    }

    private final JdbcClient jdbc;

    GitHubAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> of(UUID userId) {
        return jdbc.sql("SELECT user_id, github_user_id, login, avatar_url FROM github_account"
                        + " WHERE user_id = :userId")
                .param("userId", userId)
                .query(GitHubAccounts::map)
                .optional();
    }

    Optional<UUID> userFor(String login, Long githubUserId) {
        if ((login == null || login.isBlank()) && githubUserId == null) {
            return Optional.empty();
        }

        return jdbc.sql("SELECT user_id FROM github_account_lookup(:login, :githubId)")
                .param("login", login == null ? "" : login)
                .param("githubId", githubUserId)
                .query(UUID.class)
                .optional();
    }

    void connect(UUID userId, GitHubUser account) {
        jdbc.sql("""
                INSERT INTO github_account (user_id, github_user_id, login, avatar_url)
                VALUES (:userId, :githubId, :login, :avatar)
                ON CONFLICT (user_id) DO UPDATE
                SET github_user_id = EXCLUDED.github_user_id,
                    login = EXCLUDED.login,
                    avatar_url = EXCLUDED.avatar_url,
                    connected_at = now()
                """)
                .param("userId", userId)
                .param("githubId", account.id())
                .param("login", account.login())
                .param("avatar", account.avatarUrl())
                .update();
    }

    boolean takenByAnother(UUID userId, long githubUserId) {
        return jdbc.sql("SELECT count(*) FROM github_account WHERE github_user_id = :githubId"
                        + " AND user_id <> :userId")
                .param("githubId", githubUserId)
                .param("userId", userId)
                .query(Integer.class)
                .single() > 0;
    }

    void disconnect(UUID userId) {
        jdbc.sql("DELETE FROM github_account WHERE user_id = :userId").param("userId", userId).update();
    }

    private static Row map(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getObject("user_id", UUID.class),
                rs.getLong("github_user_id"),
                rs.getString("login"),
                rs.getString("avatar_url"));
    }
}
