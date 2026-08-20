package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import app.nowtask.identity.api.ApiKeyAuthenticator;
import app.nowtask.identity.api.ApiKeyIdentity;
import app.nowtask.shared.ActorContext;

final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ApiKeyAuthenticator authenticator;
    private final ApiErrorWriter errors;

    ApiKeyAuthenticationFilter(ApiKeyAuthenticator authenticator, ApiErrorWriter errors) {
        this.authenticator = authenticator;
        this.errors = errors;
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.startsWith(ApiKeyAuthenticator.TOKEN_PREFIX) ? token : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyIdentity> identity = authenticator.authenticate(token);
        if (identity.isEmpty()) {
            errors.write(response, 401,
                    "The API key is invalid, revoked or expired",
                    Map.of("reason", "invalid_api_key"));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiKeyAuthenticationToken(identity.get()));
        SecurityContextHolder.setContext(context);
        ActorContext.set(identity.get().agentLabel());
        try {
            chain.doFilter(request, response);
        } finally {
            ActorContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
