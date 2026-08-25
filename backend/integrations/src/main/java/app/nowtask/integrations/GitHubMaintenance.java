package app.nowtask.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class GitHubMaintenance {

    private static final Logger log = LoggerFactory.getLogger(GitHubMaintenance.class);

    private static final int DELIVERY_LIFETIME_DAYS = 7;

    private final GitHubStore store;

    GitHubMaintenance(GitHubStore store) {
        this.store = store;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT6H")
    void run() {
        try {
            int removed = store.pruneDeliveries(DELIVERY_LIFETIME_DAYS);
            if (removed > 0) {
                log.info("GitHub maintenance: {} delivery records dropped", removed);
            }
        } catch (RuntimeException e) {
            log.warn("GitHub maintenance failed", e);
        }
    }
}
