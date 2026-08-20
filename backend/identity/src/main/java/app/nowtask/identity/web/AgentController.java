package app.nowtask.identity.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.ApiKeyScope;
import app.nowtask.identity.api.ApiKeyView;
import app.nowtask.identity.api.ApiKeys;
import app.nowtask.identity.api.AuditView;
import app.nowtask.identity.api.Audits;

@RestController
@RequestMapping("/api/agents")
class AgentController {
    private static final int DEFAULT_ACTIVITY = 20;

    private final ApiKeys keys;
    private final Audits audits;

    AgentController(ApiKeys keys, Audits audits) {
        this.keys = keys;
        this.audits = audits;
    }

    record Agent(
            UUID id,
            String prefix,
            String label,
            List<String> scopes,
            Instant lastUsedAt,
            Instant expiresAt,
            String state) {
    }

    record Activity(
            UUID id,
            Instant at,
            String agent,
            String action,
            String method,
            String path,
            Integer status) {
    }

    record Overview(List<Agent> agents, List<Activity> activity) {
    }

    @GetMapping
    Overview overview(@RequestParam(name = "activity", defaultValue = "" + DEFAULT_ACTIVITY) int activity) {
        List<Agent> agents = keys.list().stream().map(AgentController::toAgent).toList();
        List<Activity> events = audits.agentActivity(activity).stream().map(AgentController::toActivity).toList();
        return new Overview(agents, events);
    }

    private static Agent toAgent(ApiKeyView view) {
        return new Agent(
                view.id(),
                view.prefix(),
                view.label(),
                view.scopes().stream().map(ApiKeyScope::code).toList(),
                view.lastUsedAt(),
                view.expiresAt(),
                view.state());
    }

    private static Activity toActivity(AuditView view) {
        Map<String, Object> detail = view.detail() == null ? Map.of() : view.detail();
        return new Activity(
                view.id(),
                view.at(),
                view.actorLabel(),
                view.action(),
                text(detail.get("method")),
                text(detail.get("path")),
                number(detail.get("status")));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer number(Object value) {
        return value instanceof Number figure ? figure.intValue() : null;
    }
}
