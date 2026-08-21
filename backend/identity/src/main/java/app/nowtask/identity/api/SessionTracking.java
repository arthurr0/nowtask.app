package app.nowtask.identity.api;

public interface SessionTracking {

    boolean track(String email, String sessionId, String ip, String userAgent);

    void forget(String sessionId);
}
