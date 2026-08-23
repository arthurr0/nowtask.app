package app.nowtask.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

class SessionCookieRefreshFilter extends OncePerRequestFilter {

    private static final String REFRESHED_AT = "sessionCookieRefreshedAt";

    private final CookieSerializer cookies;
    private final Duration interval;

    SessionCookieRefreshFilter(CookieSerializer cookies, Duration sessionTimeout) {
        this.cookies = cookies;
        this.interval = sessionTimeout.dividedBy(8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null && carriesCurrentCookie(request, session.getId()) && due(session)) {
            session.setAttribute(REFRESHED_AT, Instant.now().toEpochMilli());
            cookies.writeCookieValue(new CookieValue(request, response, session.getId()));
        }

        chain.doFilter(request, response);
    }

    private boolean carriesCurrentCookie(HttpServletRequest request, String sessionId) {
        return cookies.readCookieValues(request).contains(sessionId);
    }

    private boolean due(HttpSession session) {
        return !(session.getAttribute(REFRESHED_AT) instanceof Long millis)
                || Instant.ofEpochMilli(millis).isBefore(Instant.now().minus(interval));
    }
}
