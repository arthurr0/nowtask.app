package app.nowtask.workspace.preset;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.workspace.api.PresetViews.PresetDetailView;
import app.nowtask.workspace.api.PresetViews.PresetFieldView;
import app.nowtask.workspace.api.PresetViews.PresetRuleView;
import app.nowtask.workspace.api.PresetViews.PresetStatusView;
import app.nowtask.workspace.api.PresetViews.PresetSummaryView;
import app.nowtask.workspace.api.PresetViews.PresetTransitionView;
import app.nowtask.workspace.api.Presets;
import app.nowtask.workspace.api.Workspace;
import app.nowtask.workspace.api.WorkspaceViews.ProjectView;

@Service
@Transactional
public class PresetService implements Presets {

    private final JdbcClient jdbc;
    private final Workspace workspace;
    private final UserDirectory users;
    private final ObjectMapper json;

    PresetService(JdbcClient jdbc, Workspace workspace, UserDirectory users, ObjectMapper json) {
        this.jdbc = jdbc;
        this.workspace = workspace;
        this.users = users;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PresetSummaryView> catalog() {
        return PresetCatalog.all().stream().map(PresetService::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresetDetailView detail(String code) {
        PresetDefinition preset = PresetCatalog.find(code)
                .orElseThrow(() -> NotFoundException.of("Preset", code));

        List<PresetStatusView> statuses = new java.util.ArrayList<>();
        for (int position = 0; position < preset.statuses().size(); position++) {
            PresetDefinition.Status status = preset.statuses().get(position);
            statuses.add(new PresetStatusView(
                    status.code(), status.label(), status.category(), status.wipLimit(), position, status.swatch()));
        }

        return new PresetDetailView(
                toSummary(preset),
                statuses,
                preset.transitions().stream()
                        .map(t -> new PresetTransitionView(t.from(), t.to(), t.requirement()))
                        .toList(),
                preset.fields().stream()
                        .map(field -> new PresetFieldView(
                                field.name(),
                                field.fieldKey(),
                                field.type(),
                                field.options().stream().map(PresetDefinition.Option::value).toList()))
                        .toList(),
                preset.views().stream().map(PresetDefinition.View::code).toList(),
                preset.rules().stream()
                        .map(rule -> new PresetRuleView(rule.name(), rule.summary(), rule.supported()))
                        .toList());
    }

    @Override
    public ProjectView createProject(String name, String code, String presetCode) {
        String cleanName = required(name, "Project name");
        String cleanCode = projectCode(code, cleanName);
        String preset = presetCode == null || presetCode.isBlank() ? PresetCatalog.DEFAULT_CODE : presetCode;

        if (!PresetCatalog.isKnown(preset) && !PresetCatalog.CUSTOM_CODE.equals(preset)) {
            throw new IllegalArgumentException("Unknown preset: " + preset);
        }

        Integer taken = jdbc.sql("SELECT count(*) FROM project WHERE upper(code) = upper(?)")
                .param(cleanCode)
                .query(Integer.class)
                .single();
        if (taken != null && taken > 0) {
            throw new RuleViolationException("A project with code " + cleanCode + " already exists");
        }

        UUID projectId = UUID.randomUUID();
        int position = jdbc.sql("SELECT COALESCE(MAX(position), -1) + 1 FROM project").query(Integer.class).single();

        if (PresetCatalog.CUSTOM_CODE.equals(preset)) {
            jdbc.sql("""
                            INSERT INTO project (id, name, code, position, archived, preset_code)
                            VALUES (?, ?, ?, ?, FALSE, 'custom')
                            """)
                    .params(projectId, cleanName, cleanCode, position)
                    .update();
            return requireProject(projectId);
        }

        PresetDefinition definition = PresetCatalog.require(preset);

        jdbc.sql("""
                        INSERT INTO project (id, name, code, position, archived, preset_code, preset_applied_at,
                                             sprints_enabled, milestones_enabled, estimate_unit, wip_enforced,
                                             dependency_guard, block_disallowed_drag, default_view_code)
                        VALUES (?, ?, ?, ?, FALSE, ?, now(), ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(projectId, cleanName, cleanCode, position, definition.code(),
                        definition.sprintsEnabled(), definition.milestonesEnabled(), definition.estimateUnit(),
                        definition.wipEnforced(), definition.dependencyGuard(), definition.blockDisallowedDrag(),
                        definition.defaultViewCode())
                .update();

        Map<String, UUID> statusIds = applyStatuses(projectId, definition);
        applyTransitions(definition, statusIds);
        applyFields(projectId, definition);
        applyViews(projectId, definition, statusIds);
        applyRules(projectId, definition);
        applyMilestones(projectId, definition);

        return requireProject(projectId);
    }

    private Map<String, UUID> applyStatuses(UUID projectId, PresetDefinition definition) {
        Map<String, UUID> ids = new LinkedHashMap<>();

        for (int position = 0; position < definition.statuses().size(); position++) {
            PresetDefinition.Status status = definition.statuses().get(position);
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO status_def (id, project_id, code, label, category, wip_limit, position, swatch)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(id, projectId, status.code(), status.label(), status.category().code(),
                            status.wipLimit(), position, status.swatch())
                    .update();
            ids.put(status.code(), id);
        }

        return ids;
    }

    private void applyTransitions(PresetDefinition definition, Map<String, UUID> statusIds) {
        for (PresetDefinition.Transition transition : definition.transitions()) {
            UUID from = statusIds.get(transition.from());
            UUID to = statusIds.get(transition.to());
            if (from == null || to == null) {
                continue;
            }
            jdbc.sql("INSERT INTO status_transition (id, from_status, to_status, requirement) VALUES (?, ?, ?, ?)")
                    .params(UUID.randomUUID(), from, to, transition.requirement())
                    .update();
        }
    }

    private void applyFields(UUID projectId, PresetDefinition definition) {
        int position = 0;

        for (PresetDefinition.Field field : definition.fields()) {
            UUID fieldId = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO custom_field (id, project_id, name, field_key, type, scope_label,
                                                      restricted_to_role, required_permission, position)
                            VALUES (?, ?, ?, ?, ?, '', ?, ?, ?)
                            """)
                    .params(fieldId, projectId, field.name(), field.fieldKey(), field.type(),
                            field.restrictedToRole(),
                            field.restrictedToRole() == null ? null : "fields.view_protected",
                            position++)
                    .update();

            int optionPosition = 0;
            for (PresetDefinition.Option option : field.options()) {
                jdbc.sql("""
                                INSERT INTO custom_field_option (id, field_id, value, label, swatch, position)
                                VALUES (?, ?, ?, ?, ?, ?)
                                """)
                        .params(UUID.randomUUID(), fieldId, option.value(), option.label(), option.swatch(),
                                optionPosition++)
                        .update();
            }
        }
    }

    private void applyViews(UUID projectId, PresetDefinition definition, Map<String, UUID> statusIds) {
        int position = jdbc.sql("SELECT COALESCE(MAX(position), 0) + 1 FROM saved_view")
                .query(Integer.class)
                .single();

        for (PresetDefinition.View view : definition.views()) {
            if (!view.supported()) {
                continue;
            }

            Map<String, Object> query = new HashMap<>(view.query());
            if (view.statusCode() != null && statusIds.containsKey(view.statusCode())) {
                query.put("statusId", statusIds.get(view.statusCode()).toString());
            }

            jdbc.sql("""
                            INSERT INTO saved_view (id, code, position, name, query, shared, owner_id,
                                                    project_id, origin)
                            VALUES (?, ?, ?, '', ?::JSONB, TRUE, NULL, ?, 'preset')
                            """)
                    .params(UUID.randomUUID(), view.code(), position++, json.writeValueAsString(query), projectId)
                    .update();
        }
    }

    private void applyRules(UUID projectId, PresetDefinition definition) {
        UUID actorId = users.currentUser().id();
        int position = 0;

        for (PresetDefinition.Rule rule : definition.rules()) {
            jdbc.sql("""
                            INSERT INTO automation_rule (id, project_id, name, summary, scope_label, enabled, draft,
                                                         runs_30d, trigger_def, conditions, actions, edited_by,
                                                         edited_at, position)
                            VALUES (?, ?, ?, ?, '', ?, ?, 0, ?::JSONB, ?::JSONB, ?::JSONB, ?, CURRENT_DATE, ?)
                            """)
                    .params(UUID.randomUUID(), projectId, rule.name(), rule.summary(),
                            rule.supported(), !rule.supported(),
                            json.writeValueAsString(rule.trigger()),
                            json.writeValueAsString(rule.conditions()),
                            json.writeValueAsString(rule.actions()),
                            actorId, position++)
                    .update();
        }
    }

    private void applyMilestones(UUID projectId, PresetDefinition definition) {
        for (PresetDefinition.Milestone milestone : definition.milestones()) {
            jdbc.sql("INSERT INTO milestone (id, project_id, name, due_date) VALUES (?, ?, ?, ?)")
                    .params(UUID.randomUUID(), projectId, milestone.name(),
                            LocalDate.now().plusDays(milestone.dueInDays()))
                    .update();
        }
    }

    private ProjectView requireProject(UUID id) {
        return workspace.projectById(id).orElseThrow(() -> NotFoundException.of("Projekt", id));
    }

    private static PresetSummaryView toSummary(PresetDefinition preset) {
        return new PresetSummaryView(
                preset.code(),
                preset.statusCount(),
                preset.sprintsEnabled(),
                preset.milestonesEnabled(),
                preset.estimateUnit(),
                preset.wipEnforced(),
                preset.dependencyGuard(),
                preset.defaultViewCode());
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String projectCode(String code, String name) {
        String source = code == null || code.isBlank() ? name : code;
        String cleaned = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");

        if (cleaned.isBlank()) {
            throw new RuleViolationException("The project code has to contain letters or digits");
        }

        return cleaned.substring(0, Math.min(4, cleaned.length()));
    }
}
