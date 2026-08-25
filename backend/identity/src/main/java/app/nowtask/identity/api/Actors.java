package app.nowtask.identity.api;

import java.util.UUID;

public interface Actors {

    boolean runAs(UUID userId, UUID organizationId, String label, Runnable work);
}
