package app.nowtask.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    private String state;

    @Column(name = "oidc_subject")
    private String oidcSubject;

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

    public String getRoleCode() {
        return role;
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

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public String getState() {
        return state == null ? "active" : state;
    }

    public String getOidcSubject() {
        return oidcSubject;
    }
}
