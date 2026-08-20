package app.nowtask.automation.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.automation.AutomationService;
import app.nowtask.automation.api.AutomationViews.RuleView;
import app.nowtask.automation.api.AutomationViews.RunView;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.automation.AutomationService;
import app.nowtask.automation.api.AutomationViews.RuleView;
import app.nowtask.automation.api.AutomationViews.RunView;

@RestController
@RequestMapping("/api/rules")
class RuleController {
    private final AutomationService automations;

    RuleController(AutomationService automations) {
        this.automations = automations;
    }

    @GetMapping
    List<RuleView> rules() {
        return automations.rules();
    }

    @GetMapping("/{id}")
    RuleView rule(@PathVariable UUID id) {
        return automations.rule(id);
    }

    record RuleBody(
            String name,
            String summary,
            String scopeLabel,
            Map<String, Object> trigger,
            Map<String, Object> conditions,
            List<Map<String, Object>> actions,
            Boolean draft) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RuleView create(@RequestBody RuleBody request) {
        return automations.create(
                request.name(),
                request.summary(),
                request.scopeLabel(),
                request.trigger(),
                request.conditions(),
                request.actions(),
                Boolean.TRUE.equals(request.draft()));
    }

    @PatchMapping("/{id}")
    RuleView update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return automations.update(id, body);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        automations.delete(id);
        return ResponseEntity.noContent().build();
    }

    record RunRequest(String taskKey) {
    }

    @PostMapping("/{id}/run")
    AutomationService.RunReport run(@PathVariable UUID id, @RequestBody(required = false) RunRequest request) {
        return automations.runNow(id, request == null ? null : request.taskKey());
    }

    @PostMapping("/{id}/toggle")
    RuleView toggle(@PathVariable UUID id) {
        return automations.toggle(id);
    }

    @GetMapping("/{id}/runs")
    List<RunView> runs(@PathVariable UUID id) {
        return automations.runsOf(id);
    }

    @GetMapping("/touching/{taskKey}")
    List<RuleView> touching(@PathVariable String taskKey) {
        return automations.rulesTouching(taskKey);
    }
}
