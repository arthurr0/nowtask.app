package app.nowtask.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionAuthenticator {

    static final String ACTIVE_ORGANIZATION = "activeOrganizationId";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    SessionAuthenticator(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    public void openSession(
            String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    public void selectOrganization(HttpServletRequest request, UUID organizationId) {
        request.getSession(true).setAttribute(ACTIVE_ORGANIZATION, organizationId);
    }
}
