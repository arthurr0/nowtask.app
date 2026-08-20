package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.ApiKeyScope;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.config.ApiKeyScopeRules.Rule;

final class ApiKeyGuardFilter extends OncePerRequestFilter {
    private final ApiErrorWriter errors;
    private final AuditLog audit;

    ApiKeyGuardFilter(ApiErrorWriter errors, AuditLog audit) {
        this.errors = errors;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiKeyIdentity identity)) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        Rule rule = ApiKeyScopeRules.resolve(method, path);

        if (rule.denied()) {
            deny(request, response, identity, 403, rule.denial(), null);
            return;
        }
        if (rule.required() != null && !identity.has(rule.required())) {
            deny(request, response, identity, 403,
                    "The API key \"" + identity.label() + "\" does not have the " + rule.required().code()
                            + " scope required by " + method + " " + path
                            + ". Grant this key the " + rule.required().code()
                            + " scope or generate a new key with it.",
                    rule.required());
            return;
        }

        chain.doFilter(request, response);

        if (!isRead(method)) {
            audit.record(action(method, path), path, detail(method, path, response.getStatus(), null));
        }
    }

    private void deny(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKeyIdentity identity,
            int status,
            String message,
            ApiKeyScope required)
            throws IOException {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (required != null) {
            extra.put("requiredScope", required.code());
        }
        extra.put("grantedScopes", ApiKeyScope.ordered(identity.scopes()).stream().map(ApiKeyScope::code).toList());

        audit.record(
                "agent.denied",
                request.getRequestURI(),
                detail(request.getMethod(), request.getRequestURI(), status, required));
        errors.write(response, status, message, extra);
    }

    private static Map<String, Object> detail(String method, String path, int status, ApiKeyScope required) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", method);
        detail.put("path", path);
        detail.put("status", status);
        if (required != null) {
            detail.put("requiredScope", required.code());
        }
        return detail;
    }

    private static String action(String method, String path) {
        if (path.startsWith("/api/tasks")) {
            return "agent.task." + method.toLowerCase();
        }
        if (path.startsWith("/api/rules")) {
            return "agent.rule." + method.toLowerCase();
        }
        return "agent.request." + method.toLowerCase();
    }

    private static boolean isRead(String method) {
        return "GET".equals(method) || "HEAD".equals(method);
    }
}
