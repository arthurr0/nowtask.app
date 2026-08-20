package app.nowtask.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_rule")
public class AutomationRule {
    @Id
    private UUID id;

    private String name;

    private String summary;

    @Column(name = "scope_label")
    private String scopeLabel;

    private boolean enabled;

    private boolean draft;

    @Column(name = "runs_30d")
    private int runs30d;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_def")
    private Map<String, Object> trigger;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> conditions;

    @JdbcTypeCode(SqlTypes.JSON)
    private java.util.List<Map<String, Object>> actions;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edited_at")
    private LocalDate editedAt;

    private int position;

    protected AutomationRule() {
    }


    AutomationRule(UUID id, String name, String summary, String scopeLabel, boolean enabled, boolean draft,
            Map<String, Object> trigger, Map<String, Object> conditions,
            java.util.List<Map<String, Object>> actions, UUID editedBy, int position) {
        this.id = id;
        this.name = name;
        this.summary = summary == null ? "" : summary;
        this.scopeLabel = scopeLabel == null ? "" : scopeLabel;
        this.enabled = enabled;
        this.draft = draft;
        this.runs30d = 0;
        this.trigger = trigger;
        this.conditions = conditions;
        this.actions = actions;
        this.editedBy = editedBy;
        this.editedAt = LocalDate.now();
        this.position = position;
    }

    void setName(String name) {
        this.name = name;
    }

    void setSummary(String summary) {
        this.summary = summary;
    }

    void setScopeLabel(String scopeLabel) {
        this.scopeLabel = scopeLabel;
    }

    void setDraft(boolean draft) {
        this.draft = draft;
    }

    void setTrigger(Map<String, Object> trigger) {
        this.trigger = trigger;
    }

    void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }

    void setActions(java.util.List<Map<String, Object>> actions) {
        this.actions = actions;
    }

    void touch(UUID editedBy) {
        this.editedBy = editedBy;
        this.editedAt = LocalDate.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getScopeLabel() {
        return scopeLabel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDraft() {
        return draft;
    }

    public int getRuns30d() {
        return runs30d;
    }

    public void bumpRuns() {
        this.runs30d++;
    }

    public Map<String, Object> getTrigger() {
        return trigger;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public java.util.List<Map<String, Object>> getActions() {
        return actions;
    }

    public UUID getEditedBy() {
        return editedBy;
    }

    public LocalDate getEditedAt() {
        return editedAt;
    }

    public int getPosition() {
        return position;
    }

    public boolean triggeredBy(String kind) {
        return enabled && !draft && trigger != null && kind.equals(trigger.get("kind"));
    }

    public String triggerValue() {
        return trigger == null ? null : (String) trigger.get("value");
    }
}
