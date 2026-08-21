package app.nowtask.workspace.preset;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import app.nowtask.shared.StatusCategory;
import app.nowtask.workspace.preset.PresetDefinition.Field;
import app.nowtask.workspace.preset.PresetDefinition.Milestone;
import app.nowtask.workspace.preset.PresetDefinition.Option;
import app.nowtask.workspace.preset.PresetDefinition.Rule;
import app.nowtask.workspace.preset.PresetDefinition.Status;
import app.nowtask.workspace.preset.PresetDefinition.Transition;
import app.nowtask.workspace.preset.PresetDefinition.View;

public final class PresetCatalog {

    public static final String DEFAULT_CODE = "kanban";
    public static final String CUSTOM_CODE = "custom";

    private static final Map<String, Object> NO_CONDITIONS = Map.of("kind", "group", "join", "and",
            "children", List.of());

    private static final PresetDefinition SCRUM = new PresetDefinition(
            "scrum",
            List.of(
                    new Status("backlog", "Product backlog", StatusCategory.NOT_STARTED, null,
                            "var(--c-line-strong)"),
                    new Status("sprintBacklog", "Sprint backlog", StatusCategory.NOT_STARTED, null, "var(--c-ink-3)"),
                    new Status("inProgress", "In progress", StatusCategory.IN_FLIGHT, 8, "var(--c-ink-2)"),
                    new Status("review", "Review", StatusCategory.IN_FLIGHT, 4, "var(--c-accent)"),
                    new Status("testing", "Testing", StatusCategory.IN_FLIGHT, 4, "var(--c-signal)"),
                    new Status("done", "Done", StatusCategory.DONE, null, "var(--c-done)")),
            List.of(
                    new Transition("backlog", "sprintBacklog", "estimateRequired"),
                    new Transition("sprintBacklog", "backlog", null),
                    new Transition("sprintBacklog", "inProgress", "sprintRequired"),
                    new Transition("inProgress", "sprintBacklog", "commentRequired"),
                    new Transition("inProgress", "review", "reviewerRequired"),
                    new Transition("review", "inProgress", "commentRequired"),
                    new Transition("review", "testing", "roleReviewer"),
                    new Transition("testing", "inProgress", "commentRequired"),
                    new Transition("testing", "done", "subtasksDone"),
                    new Transition("done", "inProgress", "roleManager")),
            List.of(
                    new Field("Acceptance criteria", "acceptance_criteria", "text", null, List.of()),
                    new Field("Needs QA", "needs_qa", "toggle", null, List.of()),
                    new Field("Blocked reason", "blocked_reason", "text", null, List.of())),
            List.of(
                    new View("view.sprintBoard", Map.of("groupBy", "status"), null, true),
                    new View("view.sprintBacklog", Map.of("sort", "priority"), "sprintBacklog", true),
                    new View("view.productBacklog", Map.of("sort", "priority"), "backlog", true),
                    new View("view.myWork", Map.of("groupBy", "status"), null, true)),
            List.of(
                    new Rule("Work started", "rule.workStarted",
                            Map.of("kind", "assigned", "value", "any"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "setStatus", "value", "status.inProgress")),
                            true),
                    new Rule("Handover to review", "rule.handoverToReview",
                            Map.of("kind", "statusChanged", "value", "status.review"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "assignReviewer", "value", "reviewer"),
                                    Map.of("kind", "setDueDate", "value", "+2d")),
                            true),
                    new Rule("Sprint wrap-up", "rule.sprintWrapUp",
                            Map.of("kind", "sprintCompleted", "value", ""),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "moveToNextSprint", "value", "")),
                            false)),
            List.of(),
            true,
            false,
            "points",
            false,
            false,
            null,
            "board");

    private static final PresetDefinition KANBAN = new PresetDefinition(
            "kanban",
            List.of(
                    new Status("backlog", "Backlog", StatusCategory.NOT_STARTED, null, "var(--c-line-strong)"),
                    new Status("ready", "Ready", StatusCategory.NOT_STARTED, 10, "var(--c-ink-3)"),
                    new Status("inProgress", "In progress", StatusCategory.IN_FLIGHT, 5, "var(--c-ink-2)"),
                    new Status("review", "Review", StatusCategory.IN_FLIGHT, 3, "var(--c-accent)"),
                    new Status("done", "Done", StatusCategory.DONE, null, "var(--c-done)")),
            List.of(
                    new Transition("backlog", "ready", null),
                    new Transition("ready", "backlog", null),
                    new Transition("ready", "inProgress", "assigneeRequired"),
                    new Transition("inProgress", "ready", "commentRequired"),
                    new Transition("inProgress", "review", null),
                    new Transition("review", "inProgress", "commentRequired"),
                    new Transition("review", "done", "roleReviewer"),
                    new Transition("done", "inProgress", "roleManager")),
            List.of(
                    new Field("Class of service", "class_of_service", "select", null, List.of(
                            new Option("standard", "Standard", "var(--c-ink-2)"),
                            new Option("expedite", "Expedite", "var(--c-signal)"),
                            new Option("fixedDate", "Fixed date", "var(--c-accent)"),
                            new Option("intangible", "Intangible", "var(--c-ink-3)"))),
                    new Field("Blocked reason", "blocked_reason", "text", null, List.of())),
            List.of(
                    new View("view.flow", Map.of("groupBy", "status"), null, true),
                    new View("view.blocked", Map.of("label", "blocked", "groupBy", "assignee"), null, true),
                    new View("view.expedite", Map.of("groupBy", "status"), null, false),
                    new View("view.myWork", Map.of("groupBy", "status"), null, true)),
            List.of(
                    new Rule("Work started", "rule.workStarted",
                            Map.of("kind", "assigned", "value", "any"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "setStatus", "value", "status.inProgress")),
                            true),
                    new Rule("Blocked signal", "rule.blockedSignal",
                            Map.of("kind", "labelAdded", "value", "blocked"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "notifyChannel", "value", "#blocked")),
                            true),
                    new Rule("Backlog has gone stale", "rule.staleBacklog",
                            Map.of("kind", "scheduled", "value", "daily"),
                            Map.of("kind", "group", "join", "and", "children", List.of(
                                    Map.of("field", "status", "op", "is", "value", "backlog"))),
                            List.of(Map.of("kind", "addLabel", "value", "stale")),
                            false)),
            List.of(),
            false,
            false,
            null,
            true,
            false,
            Boolean.FALSE,
            "board");

    private static final PresetDefinition WATERFALL = new PresetDefinition(
            "waterfall",
            List.of(
                    new Status("new", "Reported", StatusCategory.NOT_STARTED, null, "var(--c-line-strong)"),
                    new Status("analysis", "Analysis", StatusCategory.IN_FLIGHT, null, "var(--c-ink-3)"),
                    new Status("design", "Design", StatusCategory.IN_FLIGHT, null, "var(--c-ink-2)"),
                    new Status("implementation", "Implementation", StatusCategory.IN_FLIGHT, null, "var(--c-accent)"),
                    new Status("verification", "Sign-off", StatusCategory.IN_FLIGHT, null, "var(--c-signal)"),
                    new Status("done", "Closed", StatusCategory.DONE, null, "var(--c-done)"),
                    new Status("onHold", "On hold", StatusCategory.NOT_STARTED, null, "var(--c-warn)")),
            List.of(
                    new Transition("new", "analysis", "dependenciesDone"),
                    new Transition("analysis", "design", "approvalRequired"),
                    new Transition("design", "implementation", "approvalRequired"),
                    new Transition("implementation", "verification", "subtasksDone"),
                    new Transition("verification", "done", "roleManager"),
                    new Transition("analysis", "new", "commentRequired"),
                    new Transition("design", "analysis", "commentRequired"),
                    new Transition("implementation", "design", "commentRequired"),
                    new Transition("verification", "implementation", "commentRequired"),
                    new Transition("done", "verification", "roleManager"),
                    new Transition("analysis", "onHold", "commentRequired"),
                    new Transition("design", "onHold", "commentRequired"),
                    new Transition("implementation", "onHold", "commentRequired"),
                    new Transition("verification", "onHold", "commentRequired"),
                    new Transition("onHold", "analysis", null),
                    new Transition("onHold", "design", null),
                    new Transition("onHold", "implementation", null),
                    new Transition("onHold", "verification", null)),
            List.of(
                    new Field("Phase owner", "phase_owner", "person", null, List.of()),
                    new Field("Approval number", "approval_ref", "text", "manager", List.of()),
                    new Field("Risk", "risk_level", "select", null, List.of(
                            new Option("low", "Low", "var(--c-done)"),
                            new Option("medium", "Medium", "var(--c-warn)"),
                            new Option("high", "High", "var(--c-signal)"))),
                    new Field("Cost", "cost_pln", "currency", "manager", List.of()),
                    new Field("Planned end", "planned_end", "date", null, List.of())),
            List.of(
                    new View("view.plan", Map.of("sort", "dueDate"), null, false),
                    new View("view.phases", Map.of("groupBy", "status"), null, true),
                    new View("view.risks", Map.of("sort", "dueDate"), null, false),
                    new View("view.blockedBy", Map.of("groupBy", "assignee"), null, false)),
            List.of(
                    new Rule("Unblock the successor", "rule.unblockSuccessor",
                            Map.of("kind", "statusChanged", "value", "status.done"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "notifyBlockedTasks", "value", "")),
                            false),
                    new Rule("On hold requires escalation", "rule.onHoldEscalation",
                            Map.of("kind", "statusChanged", "value", "status.onHold"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "notifyChannel", "value", "#escalations")),
                            true),
                    new Rule("A milestone is approaching", "rule.milestoneApproaching",
                            Map.of("kind", "scheduled", "value", "daily"),
                            NO_CONDITIONS,
                            List.of(Map.of("kind", "addLabel", "value", "at-risk"),
                                    Map.of("kind", "notifyChannel", "value", "#plan")),
                            false)),
            List.of(
                    new Milestone("Analysis sign-off", 30),
                    new Milestone("Release", 90)),
            false,
            true,
            "days",
            false,
            true,
            Boolean.TRUE,
            "timeline");

    private static final List<PresetDefinition> ALL = List.of(SCRUM, KANBAN, WATERFALL);

    private PresetCatalog() {
    }

    public static List<PresetDefinition> all() {
        return ALL;
    }

    public static Optional<PresetDefinition> find(String code) {
        return ALL.stream().filter(preset -> preset.code().equals(code)).findFirst();
    }

    public static PresetDefinition require(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + code));
    }

    public static boolean isKnown(String code) {
        return find(code).isPresent();
    }
}
