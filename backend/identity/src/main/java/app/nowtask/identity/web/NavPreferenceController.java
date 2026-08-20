package app.nowtask.identity.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.NavItemView;
import app.nowtask.identity.api.NavPreferences;

@RestController
@RequestMapping("/api/me")
class NavPreferenceController {

    private final NavPreferences preferences;

    NavPreferenceController(NavPreferences preferences) {
        this.preferences = preferences;
    }

    record NavigationRequest(List<NavItemView> items) {
    }

    @GetMapping("/navigation")
    List<NavItemView> navigation() {
        return preferences.currentNavigation();
    }

    @PutMapping("/navigation")
    List<NavItemView> replaceNavigation(@RequestBody NavigationRequest request) {
        return preferences.replaceNavigation(request.items());
    }
}
