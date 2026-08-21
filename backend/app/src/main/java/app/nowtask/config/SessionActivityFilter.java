package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.identity.api.SessionTracking;

class SessionActivityFilter extends OncePerRequestFilter {

    private final SessionTracking sessions;
    private final ApiErrorWriter errors;

    SessionActivityFilter(SessionTracking sessions, ApiErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (session == null || authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof ApiKeyIdentity
                || authentication.getPrincipal() instanceof String anonymous && "anonymousUser".equals(anonymous)) {
            chain.doFilter(request, response);
            return;
        }

        boolean alive = sessions.track(
                authentication.getName(), session.getId(), clientOf(request), request.getHeader("User-Agent"));

        if (!alive) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            errors.write(response, 401, "The session has been signed out on another device",
                    Map.of("code", "SESSION_REVOKED"));
            return;
        }

        chain.doFilter(request, response);
    }

    private static String clientOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
