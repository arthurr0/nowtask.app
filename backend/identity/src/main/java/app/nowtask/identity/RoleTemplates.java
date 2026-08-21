package app.nowtask.identity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import app.nowtask.shared.Permission;

final class RoleTemplates {

    record Template(String code, String name, int position, boolean isProtected, Set<Permission> permissions) {
    }

    static final List<Template> ALL = List.of(
            new Template("admin", "Administrator", 0, true, EnumSet.allOf(Permission.class)),
            new Template("manager", "Manager", 1, false, EnumSet.of(
                    Permission.TASKS_CREATE_EDIT,
                    Permission.TASKS_COMMENT,
                    Permission.TASKS_DELETE,
                    Permission.TASKS_STATUS_OUTSIDE_FLOW,
                    Permission.FIELDS_MANAGE,
                    Permission.FIELDS_VIEW_PROTECTED,
                    Permission.AUTOMATIONS_MANAGE,
                    Permission.AUTOMATIONS_RUN,
                    Permission.MEMBERS_INVITE,
                    Permission.MEMBERS_MANAGE,
                    Permission.PROJECTS_MANAGE,
                    Permission.SETTINGS_MANAGE,
                    Permission.DATA_EXPORT,
                    Permission.INTEGRATIONS_MANAGE,
                    Permission.AUDIT_READ)),
            new Template("member", "Member", 2, false, EnumSet.of(
                    Permission.TASKS_CREATE_EDIT,
                    Permission.TASKS_COMMENT,
                    Permission.AUTOMATIONS_RUN)),
            new Template("guest", "Guest", 3, false, EnumSet.of(Permission.TASKS_COMMENT)));

    static Set<Permission> permissionsOf(String code) {
        return ALL.stream()
                .filter(template -> template.code().equals(code))
                .findFirst()
                .map(Template::permissions)
                .orElse(Set.of());
    }

    private RoleTemplates() {
    }
}
