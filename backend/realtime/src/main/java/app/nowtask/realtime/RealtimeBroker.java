package app.nowtask.realtime;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RealtimeBroker {

    static final Duration STREAM_LIFETIME = Duration.ofMinutes(15);
    static final Duration RECONNECT_DELAY = Duration.ofSeconds(3);

    private final Map<UUID, Map<Long, Stream>> streams = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final AsyncTaskExecutor executor;

    RealtimeBroker(@Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public SseEmitter open(UUID organizationId, UUID userId) {
        Stream stream = new Stream(
                ids.incrementAndGet(), organizationId, userId, new SseEmitter(STREAM_LIFETIME.toMillis()));

        streams.computeIfAbsent(organizationId, ignored -> new ConcurrentHashMap<>()).put(stream.id, stream);

        stream.emitter.onCompletion(() -> forget(stream));
        stream.emitter.onError(error -> forget(stream));
        stream.emitter.onTimeout(stream.emitter::complete);

        if (!stream.send(SseEmitter.event()
                .comment("open")
                .reconnectTime(RECONNECT_DELAY.toMillis()))) {
            close(stream);
        }

        return stream.emitter;
    }

    public void publish(UUID organizationId, RealtimeMessage message) {
        dispatch(organizationId, message, stream -> true);
    }

    public void publishToUser(UUID organizationId, UUID userId, RealtimeMessage message) {
        dispatch(organizationId, message, stream -> stream.userId.equals(userId));
    }

    private void dispatch(UUID organizationId, RealtimeMessage message, Predicate<Stream> audience) {
        if (organizationId == null || !streams.containsKey(organizationId)) {
            return;
        }

        executor.execute(() -> {
            Map<Long, Stream> open = streams.get(organizationId);
            if (open == null) {
                return;
            }
            for (Stream stream : open.values()) {
                if (!audience.test(stream)) {
                    continue;
                }
                if (!stream.send(SseEmitter.event()
                        .name("change")
                        .data(message, MediaType.APPLICATION_JSON))) {
                    close(stream);
                }
            }
        });
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    void heartbeat() {
        for (Map<Long, Stream> open : streams.values()) {
            for (Stream stream : open.values()) {
                if (!stream.send(SseEmitter.event().comment("ping"))) {
                    close(stream);
                }
            }
        }
    }

    private void close(Stream stream) {
        forget(stream);
        try {
            stream.emitter.complete();
        } catch (RuntimeException ignored) {
            return;
        }
    }

    private void forget(Stream stream) {
        streams.computeIfPresent(stream.organizationId, (id, open) -> {
            open.remove(stream.id);
            return open.isEmpty() ? null : open;
        });
    }

    private static final class Stream {

        private final long id;
        private final UUID organizationId;
        private final UUID userId;
        private final SseEmitter emitter;
        private final Object lock = new Object();

        private Stream(long id, UUID organizationId, UUID userId, SseEmitter emitter) {
            this.id = id;
            this.organizationId = organizationId;
            this.userId = userId;
            this.emitter = emitter;
        }

        private boolean send(SseEmitter.SseEventBuilder event) {
            synchronized (lock) {
                try {
                    emitter.send(event);
                    return true;
                } catch (Exception failed) {
                    return false;
                }
            }
        }
    }
}
