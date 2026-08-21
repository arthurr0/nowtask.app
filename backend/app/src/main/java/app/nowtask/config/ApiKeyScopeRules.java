package app.nowtask.config;

import app.nowtask.identity.api.ApiKeyScope;

final class ApiKeyScopeRules {

    record Rule(ApiKeyScope required, String denial) {
        static Rule open() {
            return new Rule(null, null);
        }

        static Rule require(ApiKeyScope scope) {
            return new Rule(scope, null);
        }

        static Rule deny(String denial) {
            return new Rule(null, denial);
        }

        boolean denied() {
            return denial != null;
        }
    }

    private ApiKeyScopeRules() {
    }

    static Rule resolve(String method, String path) {
        if (path.equals("/api/meta") || path.equals("/api/auth/me") || path.equals("/api/auth/api-key")) {
            return Rule.open();
        }
        if (path.startsWith("/api/auth")) {
            return Rule.deny("Session login is not available to API keys");
        }
        if (path.startsWith("/api/organization")) {
            return Rule.deny("The organization panel, including key management, is not available to API keys");
        }
        if (path.startsWith("/api/notifications")) {
            return Rule.deny("Notifications are personal and available only to signed-in people");
        }
        if (path.startsWith("/api/integrations")) {
            return Rule.deny("Integration configuration is not available to API keys");
        }
        if (path.startsWith("/api/agents")) {
            return Rule.deny("The agent list and its activity are available only to signed-in people");
        }

        if (path.equals("/api/bootstrap") || path.startsWith("/api/workspace") || path.startsWith("/api/views")) {
            return readOnly(method, ApiKeyScope.WORKSPACE_READ,
                    "Workspace configuration is read only for API keys");
        }
        if (path.startsWith("/api/metrics")) {
            return readOnly(method, ApiKeyScope.METRICS_READ, "Metrics are read only");
        }
        if (path.startsWith("/api/export")) {
            return readOnly(method, ApiKeyScope.TASKS_READ, "Export is read only");
        }
        if (path.startsWith("/api/timeline") || path.startsWith("/api/search")) {
            return readOnly(method, ApiKeyScope.TASKS_READ, "This path is read only");
        }

        if (path.startsWith("/api/rules")) {
            if (isRead(method)) {
                return Rule.require(ApiKeyScope.RULES_READ);
            }
            if (path.endsWith("/run")) {
                return Rule.require(ApiKeyScope.RULES_RUN);
            }
            return Rule.require(ApiKeyScope.RULES_WRITE);
        }

        if (path.startsWith("/api/tasks")) {
            if (isRead(method)) {
                return Rule.require(ApiKeyScope.TASKS_READ);
            }
            if (removesTaskOrSubtask(method, path) || path.equals("/api/tasks/bulk/delete")) {
                return Rule.require(ApiKeyScope.TASKS_DELETE);
            }
            return Rule.require(ApiKeyScope.TASKS_WRITE);
        }

        return Rule.deny("Path " + path + " is not exposed to API keys");
    }

    private static boolean removesTaskOrSubtask(String method, String path) {
        if (!"DELETE".equals(method)) {
            return false;
        }
        String rest = path.substring("/api/tasks".length());
        boolean wholeTask = rest.chars().filter(character -> character == '/').count() == 1;
        return wholeTask || rest.contains("/subtasks/");
    }

    private static Rule readOnly(String method, ApiKeyScope scope, String denial) {
        return isRead(method) ? Rule.require(scope) : Rule.deny(denial);
    }

    private static boolean isRead(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }
}
