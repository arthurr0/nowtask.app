package app.nowtask.identity;

import java.util.List;
import app.nowtask.identity.api.PermissionView;
import app.nowtask.shared.Permission;

final class Permissions {
    static final List<PermissionView> CATALOG = java.util.Arrays.stream(Permission.values())
            .map(permission -> new PermissionView(permission.code(), permission.group()))
            .toList();

    private Permissions() {
    }
}
