package app.nowtask.identity.api;

import java.util.List;

public interface Audits {
    record Page(List<AuditView> items, int total) {
    }

    Page page(int page, int size);

    List<AuditView> agentActivity(int limit);
}
