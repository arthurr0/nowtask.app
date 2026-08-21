package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Permission {
    TASKS_CREATE_EDIT("tasks.create_edit", "tasks"),
    TASKS_COMMENT("tasks.comment", "tasks"),
    TASKS_DELETE("tasks.delete", "tasks"),
    TASKS_STATUS_OUTSIDE_FLOW("tasks.status_outside_flow", "tasks"),
    FIELDS_MANAGE("fields.manage", "fields"),
    FIELDS_VIEW_PROTECTED("fields.view_protected", "fields"),
    AUTOMATIONS_MANAGE("automations.manage", "automations"),
    AUTOMATIONS_RUN("automations.run", "automations"),
    MEMBERS_INVITE("members.invite", "members"),
    MEMBERS_MANAGE("members.manage", "members"),
    ROLES_MANAGE("roles.manage", "members"),
    PROJECTS_MANAGE("projects.manage", "workspace"),
    SETTINGS_MANAGE("settings.manage", "workspace"),
    DATA_EXPORT("data.export", "data"),
    APIKEYS_MANAGE("apikeys.manage", "data"),
    INTEGRATIONS_MANAGE("integrations.manage", "data"),
    AUDIT_READ("audit.read", "organization"),
    ORG_MANAGE("org.manage", "organization");

    private final String code;
    private final String group;

    Permission(String code, String group) {
        this.code = code;
        this.group = group;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String group() {
        return group;
    }

    public String authority() {
        return "PERM_" + name();
    }

    @JsonCreator
    public static Permission of(String code) {
        for (Permission value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Nieznane uprawnienie: " + code);
    }
}
