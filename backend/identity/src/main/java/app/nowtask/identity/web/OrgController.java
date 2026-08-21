package app.nowtask.identity.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import app.nowtask.identity.api.MembershipView;
import app.nowtask.identity.api.OrganizationView;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.NotFoundException;

@RestController
@RequestMapping("/api/orgs")
class OrgController {

    private static final String SESSION_KEY = "activeOrganizationId";

    private final Organizations organizations;
    private final UserDirectory directory;

    OrgController(Organizations organizations, UserDirectory directory) {
        this.organizations = organizations;
        this.directory = directory;
    }

    @GetMapping
    List<MembershipView> mine() {
        return organizations.membershipsOf(directory.currentUser().id());
    }

    @GetMapping("/current")
    OrganizationView current() {
        return organizations.current().orElseThrow(() -> new NotFoundException("No active organization"));
    }

    @GetMapping("/slug-available")
    Map<String, Object> slugAvailable(@RequestParam("slug") String slug) {
        return Map.of("available", organizations.slugAvailable(slug));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/current")
    OrganizationView updateCurrent(@RequestBody Map<String, Object> body) {
        return organizations.update(new app.nowtask.shared.PatchBody(body));
    }

    record NewOrg(String name, String slug, String presetCode) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationView create(@RequestBody NewOrg request, HttpServletRequest httpRequest) {
        UUID userId = directory.currentUser().id();
        OrganizationView created =
                organizations.create(userId, request.name(), request.slug(), request.presetCode());

        httpRequest.getSession(true).setAttribute(SESSION_KEY, created.id());
        return created;
    }

    @PostMapping("/{id}/switch")
    MembershipView switchTo(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = directory.currentUser().id();

        MembershipView membership = organizations.membership(userId, id)
                .orElseThrow(() -> new ForbiddenException("ORG_FORBIDDEN", id.toString()));

        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        httpRequest.getSession(true).setAttribute(SESSION_KEY, id);
        organizations.markSeen(userId, id);

        return membership;
    }
}
