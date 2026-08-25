package app.nowtask.integrations;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import app.nowtask.integrations.GitHubRows.Link;
import app.nowtask.integrations.api.Channels.Delivery;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.events.TaskCommands;
import app.nowtask.shared.events.TaskEvents;

@Component
class GitHubSyncListener {

    private final GitHubChannel channel;
    private final GitHubStore store;
    private final GitHubApp app;
    private final TaskCommands tasks;
    private final IntegrationRepository integrations;
    private final DeliveryLog log;
    private final AsyncTaskExecutor executor;

    GitHubSyncListener(
            GitHubChannel channel,
            GitHubStore store,
            GitHubApp app,
            TaskCommands tasks,
            IntegrationRepository integrations,
            DeliveryLog log,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.channel = channel;
        this.store = store;
        this.app = app;
        this.tasks = tasks;
        this.integrations = integrations;
        this.log = log;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onCreated(TaskEvents.TaskCreated event) {
        run(event.ruleName(), integration -> openIssue(integration, event.taskKey(), event.title()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onContentChanged(TaskEvents.TaskContentChanged event) {
        run(event.ruleName(), integration -> pushIssue(integration, event.taskKey(), event.title(),
                event.description(), null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStatusChanged(TaskEvents.TaskStatusChanged event) {
        run(event.ruleName(), integration -> {
            GitHubSettings settings = GitHubSettings.of(integration.getConfig());
            tasks.facts(event.taskKey()).ifPresent(facts -> pushIssue(integration, event.taskKey(),
                    facts.title(), null, settings.closesIssue(event.toStatusCode())));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onDeleted(TaskEvents.TaskDeleted event) {
        run(event.ruleName(), integration -> store.deleteLinksForTask(event.taskKey()));
    }

    private void openIssue(Integration integration, String taskKey, String title) {
        GitHubSettings settings = GitHubSettings.of(integration.getConfig());
        if (!settings.syncIssues() || settings.issueRepo().isBlank() || !settings.coversTask(taskKey)) {
            return;
        }
        if (store.issueLink(taskKey).isPresent()) {
            return;
        }

        Optional<Link> link = channel.openIssue(
                settings, taskKey, title, tasks.description(taskKey).orElse(""));

        link.ifPresent(created -> log.record(integration.getId(), "github.issueCreated", taskKey,
                new Delivery(true, created.repo() + " #" + created.number())));
    }

    private void pushIssue(
            Integration integration, String taskKey, String title, String description, Boolean closed) {
        GitHubSettings settings = GitHubSettings.of(integration.getConfig());
        if (!settings.syncIssues() || !settings.coversTask(taskKey)) {
            return;
        }

        Optional<Link> link = store.issueLink(taskKey);
        if (link.isEmpty()) {
            return;
        }

        String body = description != null ? description : tasks.description(taskKey).orElse("");
        boolean close = closed != null ? closed : "closed".equals(link.get().state());

        channel.pushIssue(link.get(), taskKey, title, body, close);
        log.record(integration.getId(), "github.issueUpdated", taskKey,
                new Delivery(true, link.get().repo() + " #" + link.get().number()));
    }

    private void run(String actorLabel, java.util.function.Consumer<Integration> work) {
        if (!app.configured() || GitHubActor.LABEL.equals(actorLabel)) {
            return;
        }

        OrganizationContext scope = OrganizationContextHolder.currentOrNull();
        if (scope == null || !scope.hasOrganization()) {
            return;
        }

        executor.execute(() -> OrganizationContextHolder.runAs(scope, () -> forEachIntegration(work)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void forEachIntegration(java.util.function.Consumer<Integration> work) {
        List<Integration> targets = integrations.findByEnabledTrue().stream()
                .filter(integration -> GitHubSettings.KIND.equals(integration.getKind()))
                .toList();

        for (Integration integration : targets) {
            try {
                work.accept(integration);
            } catch (RuntimeException e) {
                log.record(integration.getId(), "github.sync", null,
                        new Delivery(false, e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }
    }
}
