package app.nowtask.identity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import app.nowtask.identity.api.OnboardingView;
import app.nowtask.identity.api.OnboardingView.ChecklistItemView;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.RuleViolationException;

@Service
@Transactional
public class OnboardingService {

    public static final String FLOW_FOUNDER = "founder";
    public static final String FLOW_INVITEE = "invitee";

    public static final String STEP_ORG_NAME = "orgName";
    public static final String STEP_PRESET = "preset";
    public static final String STEP_PROJECT = "project";
    public static final String STEP_INVITE = "invite";
    public static final String STEP_TOUR = "tour";
    public static final String STEP_DONE = "done";

    static final String ITEM_CREATE_TASK = "createTask";
    static final String ITEM_MOVE_TASK = "moveTask";
    static final String ITEM_INVITE_MEMBER = "inviteMember";
    static final String ITEM_TRY_AUTOMATION = "tryAutomation";
    static final String ITEM_CUSTOMIZE_FLOW = "customizeFlow";

    private static final List<String> FOUNDER_STEPS =
            List.of(STEP_ORG_NAME, STEP_PRESET, STEP_PROJECT, STEP_INVITE, STEP_DONE);
    private static final List<String> INVITEE_STEPS = List.of(STEP_TOUR, STEP_DONE);

    private static final List<String> FOUNDER_ITEMS = List.of(
            ITEM_CREATE_TASK, ITEM_MOVE_TASK, ITEM_INVITE_MEMBER, ITEM_TRY_AUTOMATION, ITEM_CUSTOMIZE_FLOW);
    private static final List<String> INVITEE_ITEMS = List.of(
            ITEM_CREATE_TASK, ITEM_MOVE_TASK, ITEM_TRY_AUTOMATION);

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    OnboardingService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public OnboardingView start(UUID organizationId, UUID userId, String flow, String step) {
        jdbc.sql("""
                        INSERT INTO onboarding_progress (id, organization_id, user_id, flow, step)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (organization_id, user_id) DO NOTHING
                        """)
                .params(UUID.randomUUID(), organizationId, userId, flow, step)
                .update();

        return read(organizationId, userId).map(this::toView).orElseThrow(
                () -> NotFoundException.of("Onboarding", userId));
    }

    @Transactional(readOnly = true)
    public Optional<OnboardingView> current() {
        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        if (context == null || !context.hasOrganization()) {
            return Optional.empty();
        }

        return read(context.organizationId(), context.userId()).map(this::toView);
    }

    public OnboardingView patch(String step, Boolean dismissed, Boolean tourSeen) {
        OrganizationContext context = OrganizationContextHolder.current();
        Row row = read(context.organizationId(), context.userId())
                .orElseThrow(() -> new NotFoundException("Onboarding has not been started"));

        if (step != null) {
            List<String> allowed = FLOW_INVITEE.equals(row.flow()) ? INVITEE_STEPS : FOUNDER_STEPS;
            if (!allowed.contains(step)) {
                throw new IllegalArgumentException("Unknown onboarding step: " + step);
            }
            jdbc.sql("UPDATE onboarding_progress SET step = ?, updated_at = now() WHERE id = ?")
                    .params(step, row.id())
                    .update();
        }
        if (Boolean.TRUE.equals(dismissed)) {
            jdbc.sql("UPDATE onboarding_progress SET dismissed_at = now(), updated_at = now() WHERE id = ?")
                    .param(row.id())
                    .update();
        }
        if (Boolean.FALSE.equals(dismissed)) {
            jdbc.sql("UPDATE onboarding_progress SET dismissed_at = NULL, updated_at = now() WHERE id = ?")
                    .param(row.id())
                    .update();
        }
        if (Boolean.TRUE.equals(tourSeen)) {
            jdbc.sql("UPDATE onboarding_progress SET tour_seen_at = now(), updated_at = now() WHERE id = ?")
                    .param(row.id())
                    .update();
        }

        return read(context.organizationId(), context.userId())
                .map(this::toView)
                .orElseThrow(() -> new NotFoundException("Onboarding has not been started"));
    }

    public void rejectChecklistWrite() {
        throw new RuleViolationException("Checklist items are ticked off by what you do, not by the API",
                "CHECKLIST_READ_ONLY");
    }

    public void tick(UUID organizationId, UUID userId, String code) {
        if (organizationId == null || userId == null) {
            return;
        }

        Optional<Row> found = read(organizationId, userId);
        if (found.isEmpty()) {
            return;
        }

        Row row = found.get();
        List<String> items = FLOW_INVITEE.equals(row.flow()) ? INVITEE_ITEMS : FOUNDER_ITEMS;
        if (!items.contains(code) || row.checklist().containsKey(code)) {
            return;
        }

        Map<String, String> checklist = new LinkedHashMap<>(row.checklist());
        checklist.put(code, Instant.now().toString());

        jdbc.sql("UPDATE onboarding_progress SET checklist = ?::JSONB, updated_at = now() WHERE id = ?")
                .params(json.writeValueAsString(checklist), row.id())
                .update();

        if (checklist.keySet().containsAll(items)) {
            jdbc.sql("UPDATE onboarding_progress SET completed_at = now() WHERE id = ? AND completed_at IS NULL")
                    .param(row.id())
                    .update();
        }
    }

    public int dismissStale(int days) {
        Integer dismissed = jdbc.sql("SELECT dismiss_stale_onboarding(?)")
                .param(days)
                .query(Integer.class)
                .single();
        return dismissed == null ? 0 : dismissed;
    }

    private Optional<Row> read(UUID organizationId, UUID userId) {
        return jdbc.sql("""
                        SELECT id, flow, step, checklist, tour_seen_at, completed_at, dismissed_at
                        FROM onboarding_progress
                        WHERE organization_id = ? AND user_id = ?
                        """)
                .params(organizationId, userId)
                .query((rs, rowNum) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("flow"),
                        rs.getString("step"),
                        json.readValue(rs.getString("checklist"), new TypeReference<Map<String, String>>() {
                        }),
                        rs.getTimestamp("tour_seen_at") != null,
                        rs.getTimestamp("completed_at") != null,
                        rs.getTimestamp("dismissed_at") != null))
                .optional();
    }

    private OnboardingView toView(Row row) {
        List<String> items = FLOW_INVITEE.equals(row.flow()) ? INVITEE_ITEMS : FOUNDER_ITEMS;

        List<ChecklistItemView> checklist = items.stream()
                .map(code -> {
                    String at = row.checklist().get(code);
                    return new ChecklistItemView(code, at != null, at == null ? null : Instant.parse(at));
                })
                .toList();

        return new OnboardingView(
                row.flow(), row.step(), checklist, row.tourSeen(), row.completed(), row.dismissed());
    }

    private record Row(
            UUID id,
            String flow,
            String step,
            Map<String, String> checklist,
            boolean tourSeen,
            boolean completed,
            boolean dismissed) {
    }
}
