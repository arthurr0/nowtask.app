package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

class RateLimitFilter extends OncePerRequestFilter {

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/signup", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/login", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/auth/resend-verification", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/forgot-password", 5, Duration.ofHours(1)),
            new Rule("POST", "/api/auth/reset-password", 10, Duration.ofHours(1)),
            new Rule("GET", "/api/invites/", 20, Duration.ofHours(1)),
            new Rule("POST", "/api/invites/", 20, Duration.ofHours(1)));

    private static final int MAX_TRACKED_CLIENTS = 20_000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final ApiErrorWriter errors;

    RateLimitFilter(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Rule rule = ruleFor(request);

        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = rule.method() + " " + rule.prefix() + " " + clientOf(request);
        Instant now = Instant.now();
        Instant windowStart = now.minus(rule.window());

        Deque<Instant> recent = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        boolean allowed;
        long retryAfter = rule.window().toSeconds();

        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(windowStart)) {
                recent.pollFirst();
            }

            allowed = recent.size() < rule.limit();

            if (allowed) {
                recent.addLast(now);
            } else {
                Instant oldest = recent.peekFirst();
                retryAfter = Math.max(1, Duration.between(now, oldest.plus(rule.window())).toSeconds());
            }
        }

        if (hits.size() > MAX_TRACKED_CLIENTS) {
            hits.clear();
        }

        if (!allowed) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errors.write(response, 429, "Too many requests, try again later",
                    Map.of("code", "RATE_LIMITED"));
            return;
        }

        chain.doFilter(request, response);
    }

    private static Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Rule rule : RULES) {
            if (!rule.method().equals(method)) {
                continue;
            }
            if (rule.prefix().endsWith("/") ? path.startsWith(rule.prefix()) : path.equals(rule.prefix())) {
                return rule;
            }
        }

        return null;
    }

    private static String clientOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Rule(String method, String prefix, int limit, Duration window) {
    }
}
