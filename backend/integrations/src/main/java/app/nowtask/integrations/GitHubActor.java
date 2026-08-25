package app.nowtask.integrations;

import org.springframework.stereotype.Component;
import app.nowtask.identity.api.Actors;
import app.nowtask.integrations.GitHubRows.Installation;

@Component
class GitHubActor {

    static final String LABEL = "GitHub";

    private final Actors actors;

    GitHubActor(Actors actors) {
        this.actors = actors;
    }

    boolean runAs(Installation installation, Runnable work) {
        return actors.runAs(installation.connectedBy(), installation.organizationId(), LABEL, work);
    }

    boolean runAsMember(java.util.UUID userId, java.util.UUID organizationId, Runnable work) {
        return actors.runAs(userId, organizationId, LABEL, work);
    }
}
