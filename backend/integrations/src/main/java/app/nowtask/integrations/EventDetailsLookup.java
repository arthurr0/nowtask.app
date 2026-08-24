package app.nowtask.integrations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.events.TaskCommands;

@Component
class EventDetailsLookup {

    private final TaskCommands tasks;
    private final UserDirectory users;
    private final MessageSource messages;

    EventDetailsLookup(TaskCommands tasks, UserDirectory users, MessageSource messages) {
        this.tasks = tasks;
        this.users = users;
        this.messages = messages;
    }

    EventDetails of(String taskKey, UUID actorId) {
        String actor = name(actorId);
        if (taskKey == null || taskKey.isBlank()) {
            return EventDetails.of(actor);
        }

        return tasks.facts(taskKey)
                .map(facts -> new EventDetails(
                        facts.title(),
                        name(facts.assigneeId()),
                        priority(facts.priority()),
                        date(facts.dueDate()),
                        facts.labels(),
                        actor))
                .orElseGet(() -> EventDetails.of(actor));
    }

    private String name(UUID userId) {
        return userId == null ? null : users.findById(userId).map(UserView::name).orElse(null);
    }

    private String priority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = "mail.priority." + code;
        String resolved = messages.getMessage(key, null, key, MailConfig.DEFAULT_LOCALE);
        return key.equals(resolved) ? code : resolved;
    }

    private static String date(LocalDate due) {
        return due == null
                ? null
                : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(MailConfig.DEFAULT_LOCALE)
                        .format(due);
    }
}
