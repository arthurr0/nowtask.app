package app.nowtask.integrations.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.integrations.NotificationPreferenceService;
import app.nowtask.integrations.api.NotificationViews.NotificationPrefView;

@RestController
@RequestMapping("/api/account/notifications")
class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;

    NotificationPreferenceController(NotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    List<NotificationPrefView> list() {
        return preferences.list();
    }

    @PutMapping
    List<NotificationPrefView> replace(@RequestBody List<NotificationPrefView> body) {
        return preferences.replace(body == null ? List.of() : body);
    }
}
