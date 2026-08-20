package app.nowtask.identity.api;

public record PermissionView(String key, String admin, String manager, String member, String guest) {
}
