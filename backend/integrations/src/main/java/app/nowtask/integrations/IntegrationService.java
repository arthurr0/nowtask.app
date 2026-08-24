package app.nowtask.integrations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.integrations.api.Channels;
import app.nowtask.integrations.api.IntegrationViews.DeliveryView;
import app.nowtask.integrations.api.IntegrationViews.IntegrationView;
import app.nowtask.integrations.api.IntegrationViews.TestResult;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.PatchBody;
import app.nowtask.shared.RuleViolationException;

@Service
public class IntegrationService implements Channels {

    public static final String EVENT_TASK_CREATED = "taskCreated";
    public static final String EVENT_TASK_STATUS_CHANGED = "taskStatusChanged";
    public static final String EVENT_TASK_ASSIGNED = "taskAssigned";
    public static final String EVENT_RULE_NOTIFY = "ruleNotify";

    private static final Set<String> KINDS = Set.of("webhook", "email");
    private static final Set<String> EVENTS = Set.of(
            EVENT_TASK_CREATED, EVENT_TASK_STATUS_CHANGED, EVENT_TASK_ASSIGNED, EVENT_RULE_NOTIFY);

    public record NewIntegration(String kind, String name, Map<String, Object> config) {
    }

    private final IntegrationRepository integrations;
    private final IntegrationDeliveryRepository deliveries;
    private final DeliveryLog log;
    private final WebhookChannel webhooks;
    private final MailChannel mail;
    private final EventDetailsLookup details;

    IntegrationService(
            IntegrationRepository integrations,
            IntegrationDeliveryRepository deliveries,
            DeliveryLog log,
            WebhookChannel webhooks,
            MailChannel mail,
            EventDetailsLookup details) {
        this.integrations = integrations;
        this.deliveries = deliveries;
        this.log = log;
        this.webhooks = webhooks;
        this.mail = mail;
        this.details = details;
    }

    @Transactional(readOnly = true)
    public List<IntegrationView> list() {
        return integrations.findAllByOrderByCreatedAtAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public IntegrationView create(NewIntegration request) {
        String kind = request.kind() == null ? "" : request.kind().trim();
        if (!KINDS.contains(kind)) {
            throw new RuleViolationException("The integration kind has to be one of: webhook, email");
        }

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new RuleViolationException("The integration name is required");
        }
        integrations.findFirstByNameIgnoreCase(name).ifPresent(existing -> {
            throw new ConflictException("An integration named " + name + " already exists");
        });

        Map<String, Object> config = checkedConfig(kind, request.config());
        return toView(integrations.save(new Integration(kind, name, config)));
    }

    @Transactional
    public IntegrationView update(UUID id, PatchBody patch) {
        Integration integration = find(id);

        if (patch.has("name")) {
            String name = patch.text("name") == null ? "" : patch.text("name").trim();
            if (name.isBlank()) {
                throw new RuleViolationException("The integration name is required");
            }
            integrations.findFirstByNameIgnoreCase(name)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("An integration named " + name + " already exists");
                    });
            integration.setName(name);
        }

        if (patch.has("enabled")) {
            integration.setEnabled(Boolean.TRUE.equals(patch.flag("enabled")));
        }

        if (patch.has("config")) {
            if (!(patch.raw("config") instanceof Map<?, ?> raw)) {
                throw new RuleViolationException("The integration configuration has to be an object");
            }
            Map<String, Object> config = new LinkedHashMap<>();
            raw.forEach((key, value) -> config.put(String.valueOf(key), value));
            integration.setConfig(checkedConfig(integration.getKind(), config));
        }

        return toView(integration);
    }

    @Transactional
    public void delete(UUID id) {
        integrations.delete(find(id));
    }

    public TestResult test(UUID id) {
        Integration integration = find(id);
        Delivery result = send(integration, "test", null, "Test message from nowtask", EventDetails.of(null));
        log.record(integration.getId(), "test", null, result);
        return new TestResult(result.ok(), result.detail());
    }

    @Override
    public Delivery notifyChannel(String channelName, String taskKey, String message) {
        String name = channelName == null ? "" : channelName.trim();
        if (name.isBlank()) {
            return new Delivery(false, "The action does not name a channel");
        }

        Integration integration = integrations.findFirstByNameIgnoreCase(name).orElse(null);
        if (integration == null) {
            return new Delivery(false, "Channel " + name + " does not exist");
        }
        if (!integration.isEnabled()) {
            return new Delivery(false, "Channel " + name + " is disabled");
        }

        Delivery result = send(integration, EVENT_RULE_NOTIFY, taskKey, message, details.of(taskKey, null));
        log.record(integration.getId(), EVENT_RULE_NOTIFY, taskKey, result);
        return result;
    }

    public void dispatch(String event, String taskKey, String message, UUID actorId) {
        List<Integration> targets = enabledFor(event);
        if (targets.isEmpty()) {
            return;
        }

        EventDetails context = details.of(taskKey, actorId);
        for (Integration integration : targets) {
            Delivery result = send(integration, event, taskKey, message, context);
            log.record(integration.getId(), event, taskKey, result);
        }
    }

    private List<Integration> enabledFor(String event) {
        return integrations.findByEnabledTrue().stream()
                .filter(integration -> integration.listensTo(event))
                .toList();
    }

    private Delivery send(
            Integration integration, String event, String taskKey, String message, EventDetails context) {
        return "email".equals(integration.getKind())
                ? mail.send(integration, event, taskKey, message)
                : webhooks.send(integration, event, taskKey, message, context);
    }

    private Integration find(UUID id) {
        return integrations.findById(id).orElseThrow(() -> NotFoundException.of("Integration", id));
    }

    private Map<String, Object> checkedConfig(String kind, Map<String, Object> config) {
        Map<String, Object> checked = new LinkedHashMap<>(config == null ? Map.of() : config);

        if (checked.get("events") instanceof List<?> events) {
            for (Object event : events) {
                if (!EVENTS.contains(String.valueOf(event))) {
                    throw new RuleViolationException("Nieznane zdarzenie integracji: " + event);
                }
            }
        } else if (checked.containsKey("events")) {
            throw new RuleViolationException("The events field has to be a list of events");
        }

        if ("webhook".equals(kind)) {
            if (text(checked, "url").isBlank()) {
                throw new RuleViolationException("A webhook requires an address in the url field");
            }
            String format = text(checked, "format");
            if (!format.isBlank() && !WebhookFormats.NAMES.contains(format.toLowerCase(Locale.ROOT))) {
                throw new RuleViolationException(
                        "The webhook format has to be one of: generic, discord, slack");
            }
        }
        if ("email".equals(kind) && text(checked, "to").isBlank()) {
            throw new RuleViolationException("A mail integration requires an address in the to field");
        }

        return checked;
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private IntegrationView toView(Integration integration) {
        List<DeliveryView> recent = deliveries.findTop20ByIntegrationIdOrderByAtDesc(integration.getId()).stream()
                .map(row -> new DeliveryView(row.getId(), row.getAt(), row.getEvent(), row.getTaskKey(),
                        row.isOk(), row.getDetail()))
                .toList();

        return new IntegrationView(
                integration.getId(),
                integration.getKind(),
                integration.getName(),
                integration.isEnabled(),
                integration.getConfig(),
                integration.getCreatedAt(),
                integration.getLastStatus(),
                integration.getLastAt(),
                integration.getLastDetail(),
                recent);
    }
}
