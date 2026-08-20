package app.nowtask.identity.api;

import java.util.Map;

public interface AuditLog {
    void record(String action, String subject, Map<String, Object> detail);
}
