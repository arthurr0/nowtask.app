package app.nowtask.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.Announcements;
import app.nowtask.identity.api.InstanceViews.AnnouncementView;
import app.nowtask.identity.api.InstanceViews.PersonRef;
import app.nowtask.identity.api.InstanceViews.SettingsView;
import app.nowtask.identity.api.InstanceViews.SignupModeView;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class InstanceSettingsService implements Announcements {

    static final String SOURCE_SETTINGS = "settings";
    static final String SOURCE_ENV = "env";

    private static final Set<String> SIGNUP_MODES =
            Set.of(SignupService.MODE_OPEN, SignupService.MODE_INVITE_ONLY, SignupService.MODE_SSO_ONLY);
    private static final Set<String> LEVELS = Set.of("info", "warning", "critical");
    private static final int TEXT_LIMIT = 500;

    private final JdbcClient jdbc;
    private final InstanceAuditService audit;
    private final String envSignupMode;

    InstanceSettingsService(
            JdbcClient jdbc,
            InstanceAuditService audit,
            @Value("${nowtask.signup.mode:open}") String envSignupMode) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.envSignupMode = envSignupMode == null || envSignupMode.isBlank()
                ? SignupService.MODE_OPEN
                : envSignupMode.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public String effectiveSignupMode() {
        return signupMode().effective();
    }

    @Transactional(readOnly = true)
    public SignupModeView signupMode() {
        String stored = jdbc.sql("SELECT signup_mode FROM instance_settings")
                .query(String.class)
                .optional()
                .orElse(null);

        return stored == null
                ? new SignupModeView(envSignupMode, SOURCE_ENV)
                : new SignupModeView(stored, SOURCE_SETTINGS);
    }

    @Transactional(readOnly = true)
    public SettingsView settings() {
        Row row = row();

        return new SettingsView(
                row.signupMode(),
                row.signupMode() == null ? envSignupMode : row.signupMode(),
                envSignupMode,
                row.announcement(),
                row.updatedAt(),
                row.updatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementView active() {
        AnnouncementView announcement = row().announcement();

        if (announcement == null) {
            return null;
        }
        if (announcement.until() != null && !announcement.until().isAfter(Instant.now())) {
            return null;
        }
        return announcement;
    }

    public SettingsView update(UUID actorId, PatchBody patch) {
        Row current = row();

        String signupMode = current.signupMode();
        AnnouncementView announcement = current.announcement();
        Map<String, Object> changes = new LinkedHashMap<>();

        if (patch.has("signupMode")) {
            String requested = parseSignupMode(patch.raw("signupMode"));

            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", signupMode);
            change.put("to", requested);
            changes.put("signupMode", change);

            signupMode = requested;
        }

        if (patch.has("announcement")) {
            announcement = parseAnnouncement(patch.raw("announcement"));
            changes.put("announcement", announcement == null ? "cleared" : "set");
        }

        jdbc.sql("""
                        UPDATE instance_settings
                        SET signup_mode = :signupMode,
                            announcement_text = :text,
                            announcement_level = :level,
                            announcement_until = :until,
                            updated_at = now(),
                            updated_by = :actor
                        """)
                .param("signupMode", signupMode)
                .param("text", announcement == null ? null : announcement.text())
                .param("level", announcement == null ? null : announcement.level())
                .param("until", announcement == null || announcement.until() == null
                        ? null
                        : Timestamp.from(announcement.until()))
                .param("actor", actorId)
                .update();

        audit.record(actorId, "settings.update", "settings", null, null, changes);

        return settings();
    }

    private static String parseSignupMode(Object raw) {
        if (raw == null) {
            return null;
        }

        String mode = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (!SIGNUP_MODES.contains(mode)) {
            throw new IllegalArgumentException("The signup mode has to be open, invite-only or sso-only");
        }
        return mode;
    }

    private static AnnouncementView parseAnnouncement(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("The announcement has to be an object or null");
        }

        Object textValue = fields.get("text");
        String text = textValue == null ? "" : textValue.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("The announcement text is required");
        }
        if (text.length() > TEXT_LIMIT) {
            throw new RuleViolationException(
                    "The announcement text may be at most " + TEXT_LIMIT + " characters long",
                    "ANNOUNCEMENT_TOO_LONG");
        }

        Object levelValue = fields.get("level");
        String level = levelValue == null || levelValue.toString().isBlank()
                ? "info"
                : levelValue.toString().trim().toLowerCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new IllegalArgumentException("The announcement level has to be info, warning or critical");
        }

        Object untilValue = fields.get("until");
        Instant until = null;
        if (untilValue != null && !untilValue.toString().isBlank()) {
            try {
                until = OffsetDateTime.parse(untilValue.toString().trim()).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("The announcement end has to be an ISO 8601 date and time");
            }
        }

        return new AnnouncementView(text, level, until);
    }

    private Row row() {
        return jdbc.sql("""
                        SELECT s.signup_mode, s.announcement_text, s.announcement_level, s.announcement_until,
                               s.updated_at, s.updated_by, u.name AS updated_by_name
                        FROM instance_settings s
                        LEFT JOIN app_user u ON u.id = s.updated_by
                        """)
                .query((rs, rowNum) -> {
                    String text = rs.getString("announcement_text");
                    Timestamp until = rs.getTimestamp("announcement_until");
                    UUID updatedBy = rs.getObject("updated_by", UUID.class);

                    return new Row(
                            rs.getString("signup_mode"),
                            text == null || text.isBlank()
                                    ? null
                                    : new AnnouncementView(
                                            text,
                                            rs.getString("announcement_level"),
                                            until == null ? null : until.toInstant()),
                            rs.getTimestamp("updated_at").toInstant(),
                            updatedBy == null ? null : new PersonRef(updatedBy, rs.getString("updated_by_name")));
                })
                .single();
    }

    private record Row(String signupMode, AnnouncementView announcement, Instant updatedAt, PersonRef updatedBy) {
    }
}
