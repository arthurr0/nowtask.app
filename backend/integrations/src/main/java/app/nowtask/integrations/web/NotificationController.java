package app.nowtask.integrations.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.integrations.NotificationService;
import app.nowtask.integrations.api.NotificationViews.NotificationPage;
import app.nowtask.integrations.api.NotificationViews.NotificationView;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    NotificationPage list(@RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        return notifications.page(unreadOnly);
    }

    @PostMapping("/{id}/read")
    NotificationView read(@PathVariable UUID id) {
        return notifications.markRead(id);
    }

    @PostMapping("/read-all")
    Map<String, Integer> readAll() {
        return Map.of("read", notifications.markAllRead());
    }
}
