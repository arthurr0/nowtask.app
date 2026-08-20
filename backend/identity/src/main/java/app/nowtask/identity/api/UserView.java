package app.nowtask.identity.api;

import java.time.LocalDate;
import java.util.UUID;
import app.nowtask.shared.RoleId;

public record UserView(
        UUID id,
        String name,
        String shortName,
        String initials,
        String email,
        RoleId role,
        int capacity,
        boolean pending,
        LocalDate invitedOn) {
}
