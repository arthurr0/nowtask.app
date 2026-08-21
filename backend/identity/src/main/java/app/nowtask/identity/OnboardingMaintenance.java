package app.nowtask.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OnboardingMaintenance {

    private static final Logger log = LoggerFactory.getLogger(OnboardingMaintenance.class);

    private static final int REMINDER_AFTER_DAYS = 7;
    private static final int CHECKLIST_LIFETIME_DAYS = 30;

    private final InviteService invites;
    private final OnboardingService onboarding;

    OnboardingMaintenance(InviteService invites, OnboardingService onboarding) {
        this.invites = invites;
        this.onboarding = onboarding;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    void run() {
        try {
            int expired = invites.expireOverdue();
            int reminded = invites.sendReminders(REMINDER_AFTER_DAYS);
            int dismissed = onboarding.dismissStale(CHECKLIST_LIFETIME_DAYS);

            if (expired + reminded + dismissed > 0) {
                log.info("Onboarding maintenance: {} invitations expired, {} reminders sent, {} lists hidden",
                        expired, reminded, dismissed);
            }
        } catch (RuntimeException e) {
            log.warn("Onboarding maintenance failed", e);
        }
    }
}
