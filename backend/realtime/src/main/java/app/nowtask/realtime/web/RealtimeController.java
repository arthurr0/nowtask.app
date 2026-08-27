package app.nowtask.realtime.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.realtime.RealtimeBroker;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;

@RestController
class RealtimeController {

    private final RealtimeBroker broker;
    private final Organizations organizations;

    RealtimeController(RealtimeBroker broker, Organizations organizations) {
        this.broker = broker;
        this.organizations = organizations;
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
            @RequestParam(name = "org", required = false) UUID org, HttpServletResponse response) {

        OrganizationContext context = OrganizationContextHolder.current();
        UUID organizationId = resolve(context, org);

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");

        return broker.open(organizationId, context.userId());
    }

    private UUID resolve(OrganizationContext context, UUID requested) {
        if (requested == null || requested.equals(context.organizationId())) {
            return context.requireOrganizationId();
        }
        return organizations.membership(context.userId(), requested)
                .map(MembershipView::organizationId)
                .orElseThrow(() -> new ForbiddenException("ORG_FORBIDDEN", requested.toString()));
    }
}
