package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.SessionTracking;
import app.nowtask.identity.api.SessionView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.NotFoundException;

@Service
@Transactional
public class SessionService implements SessionTracking {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(30);
    private static final int MAX_TRACKED = 20_000;
    private static final int USER_AGENT_LIMIT = 400;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final UserDirectory directory;
    private final AuditLog audit;
    private final Duration idleTimeout;

    private final Map<String, Instant> touched = new ConcurrentHashMap<>();
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    SessionService(
            JdbcClient jdbc,
            AppUserRepository users,
            UserDirectory directory,
            AuditLog audit,
            @Value("${server.servlet.session.timeout:30m}") Duration idleTimeout) {
        this.jdbc = jdbc;
        this.users = users;
        this.directory = directory;
        this.audit = audit;
        this.idleTimeout = idleTimeout;
    }

    @Override
    public boolean track(String email, String sessionId, String ip, String userAgent) {
        if (sessionId == null || sessionId.isBlank() || email == null || email.isBlank()) {
            return true;
        }

        String key = SecureTokens.hash(sessionId);

        if (revoked.contains(key)) {
            return false;
        }

        Instant last = touched.get(key);
        if (last != null && last.isAfter(Instant.now().minus(TOUCH_INTERVAL))) {
            return true;
        }

        int updated = jdbc.sql("""
                        UPDATE user_session SET last_seen_at = now(), ip = ?, user_agent = ?
                        WHERE session_key = ? AND revoked_at IS NULL
                        """)
                .params(ip, shorten(userAgent), key)
                .update();

        if (updated == 0) {
            if (known(key)) {
                revoked.add(key);
                touched.remove(key);
                return false;
            }

            UUID userId = users.findByEmailIgnoreCase(email).map(AppUser::getId).orElse(null);
            if (userId == null) {
                return true;
            }

            jdbc.sql("""
                            INSERT INTO user_session (id, user_id, session_key, ip, user_agent)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (session_key) DO UPDATE SET last_seen_at = now()
                            """)
                    .params(UUID.randomUUID(), userId, key, ip, shorten(userAgent))
                    .update();
        }

        remember(key);
        return true;
    }

    @Override
    public void forget(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String key = SecureTokens.hash(sessionId);
        jdbc.sql("DELETE FROM user_session WHERE session_key = ?").param(key).update();
        touched.remove(key);
        revoked.remove(key);
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(String currentSessionId) {
        UUID userId = directory.currentUser().id();
        String current = currentSessionId == null ? "" : SecureTokens.hash(currentSessionId);

        return jdbc.sql("""
                        SELECT id, session_key, created_at, last_seen_at, ip, user_agent
                        FROM user_session
                        WHERE user_id = ? AND revoked_at IS NULL AND last_seen_at >= ?
                        ORDER BY last_seen_at DESC
                        """)
                .params(userId, Timestamp.from(Instant.now().minus(idleTimeout)))
                .query((rs, rowNum) -> new SessionView(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getString("ip"),
                        rs.getString("user_agent"),
                        current.equals(rs.getString("session_key"))))
                .list();
    }

    public void revoke(UUID id, String currentSessionId) {
        UUID userId = directory.currentUser().id();

        String key = jdbc.sql("SELECT session_key FROM user_session WHERE id = ? AND user_id = ?")
                .params(id, userId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> NotFoundException.of("Session", id.toString()));

        jdbc.sql("UPDATE user_session SET revoked_at = now() WHERE id = ?").param(id).update();
        revoked.add(key);
        touched.remove(key);

        if (currentSessionId != null && key.equals(SecureTokens.hash(currentSessionId))) {
            forget(currentSessionId);
        }

        audit.record("account.sessionRevoked", directory.currentUser().email(), Map.of());
    }

    public int revokeOthers(String currentSessionId) {
        UUID userId = directory.currentUser().id();
        int closed = revokeAllExcept(userId, currentSessionId);

        audit.record("account.sessionsRevoked", directory.currentUser().email(), Map.of("closed", closed));
        return closed;
    }

    public int revokeAllExcept(UUID userId, String currentSessionId) {
        String current = currentSessionId == null ? "" : SecureTokens.hash(currentSessionId);

        List<String> keys = jdbc.sql("""
                        SELECT session_key FROM user_session
                        WHERE user_id = ? AND revoked_at IS NULL AND session_key <> ?
                        """)
                .params(userId, current)
                .query(String.class)
                .list();

        if (keys.isEmpty()) {
            return 0;
        }

        jdbc.sql("UPDATE user_session SET revoked_at = now() WHERE user_id = ? AND session_key <> ?"
                        + " AND revoked_at IS NULL")
                .params(userId, current)
                .update();

        keys.forEach(key -> {
            revoked.add(key);
            touched.remove(key);
        });

        return keys.size();
    }

    public void dropAll(UUID userId) {
        List<String> keys = jdbc.sql("SELECT session_key FROM user_session WHERE user_id = ? AND revoked_at IS NULL")
                .param(userId)
                .query(String.class)
                .list();

        jdbc.sql("UPDATE user_session SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL")
                .param(userId)
                .update();

        keys.forEach(key -> {
            revoked.add(key);
            touched.remove(key);
        });
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    void purge() {
        try {
            Timestamp before = Timestamp.from(Instant.now().minus(idleTimeout));

            jdbc.sql("DELETE FROM user_session WHERE last_seen_at < ? OR revoked_at < ?")
                    .params(before, before)
                    .update();
        } catch (RuntimeException e) {
            log.warn("Session cleanup failed", e);
        }

        Instant stale = Instant.now().minus(TOUCH_INTERVAL);
        touched.entrySet().removeIf(entry -> entry.getValue().isBefore(stale));

        if (revoked.size() > MAX_TRACKED) {
            revoked.clear();
        }
    }

    private boolean known(String key) {
        return jdbc.sql("SELECT count(*) FROM user_session WHERE session_key = ?")
                .param(key)
                .query(Integer.class)
                .single() > 0;
    }

    private void remember(String key) {
        if (touched.size() > MAX_TRACKED) {
            Instant stale = Instant.now().minus(TOUCH_INTERVAL);
            touched.entrySet().removeIf(entry -> entry.getValue().isBefore(stale));
        }
        touched.put(key, Instant.now());
    }

    private static String shorten(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > USER_AGENT_LIMIT ? userAgent.substring(0, USER_AGENT_LIMIT) : userAgent;
    }
}
