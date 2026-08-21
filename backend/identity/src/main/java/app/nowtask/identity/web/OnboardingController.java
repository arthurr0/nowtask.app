package app.nowtask.identity.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.OnboardingService;
import app.nowtask.identity.api.OnboardingView;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.PatchBody;

@RestController
@RequestMapping("/api/onboarding")
class OnboardingController {

    private final OnboardingService onboarding;

    OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping
    ResponseEntity<OnboardingView> current() {
        return onboarding.current()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/start")
    OnboardingView start() {
        OrganizationContext context = OrganizationContextHolder.current();
        return onboarding.start(
                context.requireOrganizationId(),
                context.userId(),
                OnboardingService.FLOW_FOUNDER,
                OnboardingService.STEP_PRESET);
    }

    @PatchMapping
    OnboardingView patch(@RequestBody Map<String, Object> body) {
        PatchBody patch = new PatchBody(body);

        if (patch.has("checklist")) {
            onboarding.rejectChecklistWrite();
        }

        return onboarding.patch(
                patch.has("step") ? patch.text("step") : null,
                patch.has("dismissed") ? patch.flag("dismissed") : null,
                patch.has("tourSeen") ? patch.flag("tourSeen") : null);
    }
}
