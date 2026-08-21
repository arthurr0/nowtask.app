package app.nowtask.identity.api;

import java.time.LocalDate;
import java.util.UUID;

public record UserView(
        UUID id,
        String name,
        String shortName,
        String initials,
        String email,
        RoleRefView role,
        int capacity,
        boolean pending,
        LocalDate invitedOn,
        boolean emailVerified) {
}
