package app.nowtask.identity;

import java.util.List;
import app.nowtask.identity.api.PermissionView;

final class Permissions {
    static final List<PermissionView> ALL = List.of(
            new PermissionView("perm.createEdit", "yes", "yes", "yes", "no"),
            new PermissionView("perm.comment", "yes", "yes", "yes", "yes"),
            new PermissionView("perm.deleteTasks", "yes", "yes", "no", "no"),
            new PermissionView("perm.statusOutsideFlow", "yes", "conditional", "no", "no"),
            new PermissionView("perm.manageFields", "yes", "yes", "no", "no"),
            new PermissionView("perm.manageAutomations", "yes", "yes", "no", "no"),
            new PermissionView("perm.runRules", "yes", "yes", "yes", "no"),
            new PermissionView("perm.viewProtected", "yes", "yes", "no", "no"),
            new PermissionView("perm.invite", "yes", "conditional", "no", "no"),
            new PermissionView("perm.export", "yes", "yes", "no", "no"),
            new PermissionView("perm.apiKeys", "yes", "no", "no", "no"));

    private Permissions() {
    }
}
