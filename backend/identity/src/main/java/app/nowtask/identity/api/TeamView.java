package app.nowtask.identity.api;

import java.util.UUID;

public record TeamView(UUID id, String name, int headcount) {
}
