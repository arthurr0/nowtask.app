package app.nowtask.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class InstanceAdminGrants implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InstanceAdminGrants.class);

    private final JdbcClient jdbc;
    private final InstanceAuditService audit;
    private final Set<String> emails;
    private final boolean trustUnverified;

    InstanceAdminGrants(
            JdbcClient jdbc,
            InstanceAuditService audit,
            @Value("${nowtask.instance.admins:}") String configured,
            @Value("${spring.mail.host:}") String mailHost) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.emails = parse(configured);
        this.trustUnverified = mailHost == null || mailHost.isBlank();
    }

    static Set<String> parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configured.split("[,;\\s]+"))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            grantListed();
        } catch (RuntimeException e) {
            log.warn("Granting the configured instance administrators failed", e);
        }
    }

    public int grantListed() {
        if (emails.isEmpty()) {
            return 0;
        }

        List<Granted> granted = jdbc.sql("""
                        UPDATE app_user SET instance_admin = TRUE
                        WHERE NOT instance_admin
                          AND state = 'active'
                          AND lower(email) IN (:emails)
                          AND (:trustUnverified OR email_verified_at IS NOT NULL)
                        RETURNING id, email
                        """)
                .param("emails", List.copyOf(emails))
                .param("trustUnverified", trustUnverified)
                .query((rs, rowNum) -> new Granted(rs.getObject("id", UUID.class), rs.getString("email")))
                .list();

        granted.forEach(this::record);
        return granted.size();
    }

    void grantIfListed(UUID userId) {
        if (emails.isEmpty()) {
            return;
        }

        jdbc.sql("""
                        UPDATE app_user SET instance_admin = TRUE
                        WHERE id = :id
                          AND NOT instance_admin
                          AND state = 'active'
                          AND lower(email) IN (:emails)
                          AND (:trustUnverified OR email_verified_at IS NOT NULL)
                        RETURNING id, email
                        """)
                .param("id", userId)
                .param("emails", List.copyOf(emails))
                .param("trustUnverified", trustUnverified)
                .query((rs, rowNum) -> new Granted(rs.getObject("id", UUID.class), rs.getString("email")))
                .list()
                .forEach(this::record);
    }

    private void record(Granted granted) {
        audit.record(null, "user.instance_admin_grant", "user", granted.id().toString(), granted.email(),
                Map.of("source", "env"));
    }

    private record Granted(UUID id, String email) {
    }
}
