package app.nowtask.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import app.nowtask.shared.RoleId;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private UUID id;

    private String name;

    @Column(name = "short_name")
    private String shortName;

    private String initials;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String role;

    private int capacity;

    private boolean pending;

    @Column(name = "invited_on")
    private LocalDate invitedOn;

    protected AppUser() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getInitials() {
        return initials;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public RoleId getRole() {
        return RoleId.of(role);
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isPending() {
        return pending;
    }

    public LocalDate getInvitedOn() {
        return invitedOn;
    }
}
