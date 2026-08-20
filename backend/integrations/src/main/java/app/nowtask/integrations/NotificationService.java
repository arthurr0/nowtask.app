package app.nowtask.integrations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.integrations.api.NotificationViews.NotificationPage;
import app.nowtask.integrations.api.NotificationViews.NotificationView;
import app.nowtask.shared.NotFoundException;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserDirectory users;

    NotificationService(NotificationRepository notifications, UserDirectory users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public NotificationPage page(boolean unreadOnly) {
        UUID me = users.currentUser().id();
        List<Notification> rows = unreadOnly
                ? notifications.findTop50ByUserIdAndReadAtIsNullOrderByAtDesc(me)
                : notifications.findTop50ByUserIdOrderByAtDesc(me);

        return new NotificationPage(rows.stream().map(NotificationService::toView).toList(),
                notifications.countByUserIdAndReadAtIsNull(me));
    }

    public NotificationView markRead(UUID id) {
        UUID me = users.currentUser().id();
        Notification notification = notifications.findById(id)
                .filter(row -> row.getUserId().equals(me))
                .orElseThrow(() -> NotFoundException.of("Powiadomienie", id));

        notification.markRead();
        return toView(notification);
    }

    public int markAllRead() {
        List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(users.currentUser().id());
        unread.forEach(Notification::markRead);
        return unread.size();
    }

    public void create(UUID userId, String kind, String titleKey, Map<String, Object> params, String taskKey) {
        if (userId == null) {
            return;
        }
        notifications.save(new Notification(userId, kind, titleKey, params, taskKey));
    }

    private static NotificationView toView(Notification row) {
        return new NotificationView(row.getId(), row.getAt(), row.getKind(), row.getTitleKey(),
                row.getParams(), row.getTaskKey(), row.isRead());
    }
}
