package app.nowtask.identity;

import java.util.List;
import app.nowtask.identity.api.PermissionView;

public final class PermissionsAccess {
    private PermissionsAccess() {
    }

    public static List<PermissionView> all() {
        return Permissions.ALL;
    }
}
