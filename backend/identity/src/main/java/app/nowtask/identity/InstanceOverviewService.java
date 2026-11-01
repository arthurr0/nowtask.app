package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.InstanceMail;
import app.nowtask.identity.api.InstanceViews.Counts;
import app.nowtask.identity.api.InstanceViews.Health;
import app.nowtask.identity.api.InstanceViews.Overview;

@Service
@Transactional(readOnly = true)
public class InstanceOverviewService {

    private static final Logger log = LoggerFactory.getLogger(InstanceOverviewService.class);

    private final JdbcClient jdbc;
    private final InstanceSettingsService settings;
    private final InstanceMail mail;
    private final String version;
    private final Duration idleTimeout;
    private final boolean githubConfigured;

    InstanceOverviewService(
            JdbcClient jdbc,
            InstanceSettingsService settings,
            InstanceMail mail,
            @Value("${nowtask.version:0.1.0}") String version,
            @Value("${server.servlet.session.timeout:30m}") Duration idleTimeout,
            @Value("${nowtask.github.app-id:}") String githubAppId,
            @Value("${nowtask.github.private-key:}") String githubPrivateKey,
            @Value("${nowtask.github.webhook-secret:}") String githubWebhookSecret) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.mail = mail;
        this.version = version;
        this.idleTimeout = idleTimeout;
        this.githubConfigured = !githubAppId.isBlank() && !githubPrivateKey.isBlank()
                && !githubWebhookSecret.isBlank();
    }

    public Overview overview() {
        return new Overview(
                version,
                schemaVersion(),
                counts(),
                jdbc.sql("""
                                SELECT count(*) FROM app_user
                                WHERE state IN ('active', 'disabled') AND created_at >= now() - INTERVAL '30 days'
                                """)
                        .query(Long.class)
                        .single(),
                settings.signupMode(),
                new Health(
                        databaseHealth(),
                        mail.configured() ? "configured" : "not_configured",
                        githubConfigured ? "configured" : "not_configured"));
    }

    private Counts counts() {
        long[] organizations = jdbc.sql(
                        "SELECT organizations, organizations_suspended, tasks FROM instance_organization_counts()")
                .query((rs, rowNum) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)})
                .single();

        long[] users = jdbc.sql("""
                        SELECT count(*) FILTER (WHERE state IN ('active', 'disabled')),
                               count(*) FILTER (WHERE state = 'disabled'),
                               count(*) FILTER (WHERE instance_admin AND state IN ('active', 'disabled'))
                        FROM app_user
                        """)
                .query((rs, rowNum) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)})
                .single();

        long sessions = jdbc.sql("""
                        SELECT count(*) FROM user_session
                        WHERE revoked_at IS NULL AND last_seen_at >= :since
                        """)
                .param("since", Timestamp.from(Instant.now().minus(idleTimeout)))
                .query(Long.class)
                .single();

        return new Counts(organizations[0], organizations[1], users[0], users[1], users[2], organizations[2], sessions);
    }

    private String schemaVersion() {
        try {
            return jdbc.sql("""
                            SELECT version FROM flyway_schema_history
                            WHERE success AND version IS NOT NULL
                            ORDER BY installed_rank DESC LIMIT 1
                            """)
                    .query(String.class)
                    .optional()
                    .orElse("unknown");
        } catch (RuntimeException e) {
            log.debug("The schema version is not readable", e);
            return "unknown";
        }
    }

    private String databaseHealth() {
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            return "up";
        } catch (RuntimeException e) {
            return "down";
        }
    }
}
