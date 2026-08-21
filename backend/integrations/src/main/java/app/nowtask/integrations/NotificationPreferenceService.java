package app.nowtask.integrations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.integrations.api.NotificationViews.NotificationPrefView;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class NotificationPreferenceService {

    static final List<String> KINDS = List.of("assigned", "inviteRenewal");

    private static final Preference DEFAULT = new Preference(true, false);

    private final JdbcClient jdbc;
    private final UserDirectory users;

    NotificationPreferenceService(JdbcClient jdbc, UserDirectory users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<NotificationPrefView> list() {
        return list(users.currentUser().id());
    }

    public List<NotificationPrefView> replace(List<NotificationPrefView> wanted) {
        UUID userId = users.currentUser().id();

        for (NotificationPrefView pref : wanted) {
            if (!KINDS.contains(pref.kind())) {
                throw new RuleViolationException("Unknown notification " + pref.kind(), "NOTIFICATION_UNKNOWN");
            }

            jdbc.sql("""
                            INSERT INTO user_notification_pref (user_id, kind, in_app, email)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (user_id, kind) DO UPDATE SET in_app = EXCLUDED.in_app, email = EXCLUDED.email
                            """)
                    .params(userId, pref.kind(), pref.inApp(), pref.email())
                    .update();
        }

        return list(userId);
    }

    Preference of(UUID userId, String kind) {
        if (!KINDS.contains(kind)) {
            return DEFAULT;
        }

        return jdbc.sql("SELECT in_app, email FROM user_notification_pref WHERE user_id = ? AND kind = ?")
                .params(userId, kind)
                .query((rs, rowNum) -> new Preference(rs.getBoolean("in_app"), rs.getBoolean("email")))
                .optional()
                .orElse(DEFAULT);
    }

    private List<NotificationPrefView> list(UUID userId) {
        Map<String, Preference> stored = new LinkedHashMap<>();

        jdbc.sql("SELECT kind, in_app, email FROM user_notification_pref WHERE user_id = ?")
                .param(userId)
                .query((rs, rowNum) -> Map.entry(
                        rs.getString("kind"), new Preference(rs.getBoolean("in_app"), rs.getBoolean("email"))))
                .list()
                .forEach(entry -> stored.put(entry.getKey(), entry.getValue()));

        return KINDS.stream()
                .map(kind -> {
                    Preference preference = stored.getOrDefault(kind, DEFAULT);
                    return new NotificationPrefView(kind, preference.inApp(), preference.email());
                })
                .toList();
    }

    record Preference(boolean inApp, boolean email) {
    }
}
