package app.nowtask.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.BulkInviteResultView;
import app.nowtask.identity.api.BulkInviteResultView.FailedInvite;
import app.nowtask.identity.api.InvitePreviewView;
import app.nowtask.identity.api.InviteView;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.Permission;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.events.IdentityEvents;

@Service
@Transactional
public class InviteService {

    static final Duration VALIDITY = Duration.ofDays(14);
    static final int DAILY_LIMIT = 50;
    private static final int MAX_BULK = 50;

    private static final String SELECT = """
            SELECT i.id, i.organization_id, i.email, i.role_id, i.state, i.invited_by,
                   i.created_at, i.expires_at, i.accepted_at, i.revoked_at, i.new_requested_at,
                   r.code AS role_code, r.name AS role_name,
                   u.name AS invited_by_name,
                   o.name AS organization_name, o.slug AS organization_slug, o.sso_domain
            FROM organization_invite i
            JOIN organization_role r ON r.id = i.role_id
            JOIN app_user u          ON u.id = i.invited_by
            JOIN organization o      ON o.id = i.organization_id
            """;

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final RoleService roles;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    InviteService(
            JdbcClient jdbc,
            AppUserRepository users,
            RoleService roles,
            AuditLog audit,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.events = events;
    }

    public List<InviteView> list(String state) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        expireOverdue(organizationId);

        String sql = SELECT + " WHERE i.organization_id = ?"
                + (state == null || state.isBlank() ? "" : " AND i.state = ?")
                + " ORDER BY i.created_at DESC";

        return (state == null || state.isBlank()
                ? jdbc.sql(sql).param(organizationId)
                : jdbc.sql(sql).params(organizationId, state))
                .query(InviteService::toRow)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }

    public InviteView invite(String email, String roleCode) {
        UUID organizationId = requireInviter();
        String cleanEmail = requireEmail(email);
        UUID roleId = resolveRole(organizationId, roleCode);

        guardDailyLimit(organizationId, 1);
        guardNotMember(organizationId, cleanEmail);
        guardNotInvited(organizationId, cleanEmail);

        return issue(organizationId, cleanEmail, roleId, false);
    }

    public BulkInviteResultView inviteBulk(List<String> emails, String roleCode) {
        UUID organizationId = requireInviter();
        UUID roleId = resolveRole(organizationId, roleCode);

        List<String> requested = emails == null ? List.of() : emails.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .limit(MAX_BULK)
                .toList();

        List<InviteView> sent = new ArrayList<>();
        List<FailedInvite> failed = new ArrayList<>();

        for (String email : requested) {
            try {
                guardDailyLimit(organizationId, 1);
                String cleanEmail = requireEmail(email);
                guardNotMember(organizationId, cleanEmail);
                guardNotInvited(organizationId, cleanEmail);
                sent.add(issue(organizationId, cleanEmail, roleId, false));
            } catch (RuleViolationException | ConflictException | IllegalArgumentException e) {
                failed.add(new FailedInvite(email, codeOf(e), "invite.error." + codeOf(e)));
            }
        }

        return new BulkInviteResultView(sent, failed);
    }

    public InviteView resend(UUID id) {
        UUID organizationId = requireInviter();
        Row row = require(organizationId, id);

        if (!"open".equals(row.state()) && !"expired".equals(row.state())) {
            throw new RuleViolationException("Only an open invitation can be resent", "INVITE_" + row.state()
                    .toUpperCase(Locale.ROOT));
        }

        guardDailyLimit(organizationId, 1);
        jdbc.sql("UPDATE organization_invite SET state = 'revoked', revoked_at = now() WHERE id = ?")
                .param(id)
                .update();

        return issue(organizationId, row.email(), row.roleId(), true);
    }

    public void revoke(UUID id) {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        OrganizationContextHolder.current().require(Permission.MEMBERS_INVITE);
        Row row = require(organizationId, id);

        jdbc.sql("UPDATE organization_invite SET state = 'revoked', revoked_at = now() WHERE id = ? AND state = 'open'")
                .param(id)
                .update();

        audit.record("invite.revoke", row.email(), Map.of());
    }

    @Transactional(readOnly = true)
    public InvitePreviewView preview(String token) {
        Optional<Row> found = byToken(token);
        if (found.isEmpty()) {
            return InvitePreviewView.unknown();
        }

        Row row = found.get();
        String state = effectiveState(row);

        if ("revoked".equals(state)) {
            return InvitePreviewView.unknown();
        }

        boolean accountExists = users.findByEmailIgnoreCase(row.email()).isPresent();

        return new InvitePreviewView(
                row.organizationName(),
                row.organizationSlug(),
                firstName(row.invitedByName()),
                row.roleName(),
                SecureTokens.maskEmail(row.email()),
                row.expiresAt(),
                state,
                accountExists,
                row.ssoDomain() != null && !row.ssoDomain().isBlank());
    }

    public Accepted accept(String token, UUID userId) {
        Row row = byToken(token).orElseThrow(() -> new NotFoundException("Unknown invitation"));
        String state = effectiveState(row);

        switch (state) {
            case "expired" -> throw new RuleViolationException("The invitation has expired", "INVITE_EXPIRED");
            case "revoked" -> throw new RuleViolationException("The invitation was revoked", "INVITE_REVOKED");
            case "accepted" -> throw new RuleViolationException("The invitation has already been used",
                    "INVITE_ACCEPTED");
            default -> {
            }
        }

        boolean alreadyMember = jdbc.sql(
                        "SELECT count(*) FROM organization_member WHERE organization_id = ? AND user_id = ?")
                .params(row.organizationId(), userId)
                .query(Integer.class)
                .single() > 0;

        if (alreadyMember) {
            close(row, userId);
            throw new ConflictException("You already belong to this organization", "ALREADY_MEMBER");
        }

        jdbc.sql("""
                        INSERT INTO organization_member (id, organization_id, user_id, role_id, capacity, state, joined_at)
                        VALUES (?, ?, ?, ?, 0, 'active', now())
                        """)
                .params(UUID.randomUUID(), row.organizationId(), userId, row.roleId())
                .update();

        close(row, userId);

        AppUser user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        AppUser inviter = users.findById(row.invitedBy()).orElse(null);
        Instant joinedAt = Instant.now();

        events.publishEvent(new IdentityEvents.InviteAccepted(
                row.organizationId(), row.id(), userId, row.email(), joinedAt));
        events.publishEvent(new IdentityEvents.MemberJoined(
                row.organizationId(),
                userId,
                user.getName(),
                user.getEmail(),
                row.organizationName(),
                row.invitedBy(),
                firstName(row.invitedByName()),
                inviter == null ? null : inviter.getEmail(),
                LocaleContextHolder.getLocale().getLanguage(),
                joinedAt));

        return new Accepted(row.organizationId(), row.id(), row.email(), row.roleCode());
    }

    public void requestNew(String token) {
        Row row = byToken(token).orElseThrow(() -> new NotFoundException("Unknown invitation"));

        if (!"expired".equals(effectiveState(row))) {
            throw new RuleViolationException("A new invitation can only be requested for an expired one",
                    "INVITE_NOT_EXPIRED");
        }
        if (row.newRequestedAt() != null && row.newRequestedAt().isAfter(Instant.now().minus(Duration.ofDays(1)))) {
            throw new RuleViolationException("A new invitation has already been requested today", "ALREADY_REQUESTED");
        }

        jdbc.sql("UPDATE organization_invite SET new_requested_at = now() WHERE id = ?").param(row.id()).update();

        events.publishEvent(new IdentityEvents.InviteRenewalRequested(
                row.organizationId(), row.id(), row.invitedBy(), row.email(), Instant.now()));
    }

    public int sendReminders(int afterDays) {
        List<Reminder> due = jdbc.sql("SELECT * FROM invites_due_for_reminder(?)")
                .param(afterDays)
                .query((rs, rowNum) -> new Reminder(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("email"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("role_name"),
                        rs.getString("invited_by_name"),
                        rs.getString("organization_name"),
                        rs.getString("locale")))
                .list();

        for (Reminder reminder : due) {
            String token = SecureTokens.generate();

            jdbc.sql("UPDATE organization_invite SET token_hash = ?, reminder_sent_at = now() WHERE id = ?")
                    .params(SecureTokens.hash(token), reminder.id())
                    .update();

            events.publishEvent(new IdentityEvents.InviteIssued(
                    reminder.organizationId(),
                    reminder.id(),
                    reminder.organizationName(),
                    reminder.email(),
                    firstName(reminder.invitedByName()),
                    reminder.roleName(),
                    token,
                    reminder.expiresAt(),
                    reminder.locale(),
                    true));
        }

        return due.size();
    }

    public int expireOverdue() {
        Integer expired = jdbc.sql("SELECT expire_overdue_invites()").query(Integer.class).single();
        return expired == null ? 0 : expired;
    }

    @Transactional(readOnly = true)
    public List<InviteView> openInvitesOf(UUID organizationId) {
        return jdbc.sql(SELECT + " WHERE i.organization_id = ? AND i.state = 'open' ORDER BY i.created_at DESC")
                .param(organizationId)
                .query(InviteService::toRow)
                .list()
                .stream()
                .map(Row::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> organizationOfInvite(String token) {
        return byToken(token).map(Row::organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<Target> target(String token) {
        return byToken(token).map(row -> new Target(
                row.organizationId(),
                row.organizationName(),
                row.email(),
                row.roleCode(),
                effectiveState(row),
                users.findByEmailIgnoreCase(row.email()).map(AppUser::getId).orElse(null)));
    }

    private InviteView issue(UUID organizationId, String email, UUID roleId, boolean reminder) {
        String token = SecureTokens.generate();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(VALIDITY);
        UUID inviterId = OrganizationContextHolder.current().userId();

        jdbc.sql("""
                        INSERT INTO organization_invite
                            (id, organization_id, email, role_id, token_hash, state, invited_by, expires_at, locale)
                        VALUES (?, ?, ?, ?, ?, 'open', ?, ?, ?)
                        """)
                .params(id, organizationId, email, roleId, SecureTokens.hash(token), inviterId,
                        java.sql.Timestamp.from(expiresAt), LocaleContextHolder.getLocale().getLanguage())
                .update();

        Row row = require(organizationId, id);
        audit.record("invite.create", email, Map.of("role", row.roleCode()));

        events.publishEvent(new IdentityEvents.InviteIssued(
                organizationId,
                id,
                row.organizationName(),
                email,
                firstName(row.invitedByName()),
                row.roleName(),
                token,
                expiresAt,
                LocaleContextHolder.getLocale().getLanguage(),
                reminder));

        return row.toView();
    }

    private void close(Row row, UUID userId) {
        jdbc.sql("""
                        UPDATE organization_invite
                        SET state = 'accepted', accepted_at = now(), accepted_by = ?
                        WHERE id = ?
                        """)
                .params(userId, row.id())
                .update();
    }

    private UUID requireInviter() {
        UUID organizationId = OrganizationContextHolder.currentOrganizationId();
        OrganizationContextHolder.current().require(Permission.MEMBERS_INVITE);

        UUID userId = OrganizationContextHolder.current().userId();
        boolean verified = jdbc.sql("SELECT email_verified_at IS NOT NULL FROM app_user WHERE id = ?")
                .param(userId)
                .query(Boolean.class)
                .optional()
                .orElse(false);

        if (!verified) {
            throw new RuleViolationException(
                    "Confirm your email address before sending invitations", "EMAIL_NOT_VERIFIED");
        }

        return organizationId;
    }

    private UUID resolveRole(UUID organizationId, String roleCode) {
        String code = roleCode == null || roleCode.isBlank() ? "member" : roleCode.trim().toLowerCase(Locale.ROOT);
        return roles.roleIdByCode(organizationId, code);
    }

    private void guardDailyLimit(UUID organizationId, int adding) {
        int issuedToday = jdbc.sql("""
                        SELECT count(*) FROM organization_invite
                        WHERE organization_id = ? AND created_at >= now() - INTERVAL '1 day'
                        """)
                .param(organizationId)
                .query(Integer.class)
                .single();

        if (issuedToday + adding > DAILY_LIMIT) {
            throw new RuleViolationException(
                    "The daily invitation limit (" + DAILY_LIMIT + ") has been reached", "LIMIT_REACHED");
        }
    }

    private void guardNotMember(UUID organizationId, String email) {
        int members = jdbc.sql("""
                        SELECT count(*)
                        FROM organization_member m
                        JOIN app_user u ON u.id = m.user_id
                        WHERE m.organization_id = ? AND lower(u.email) = ?
                        """)
                .params(organizationId, email)
                .query(Integer.class)
                .single();

        if (members > 0) {
            throw new ConflictException("This person is already in the organization", "ALREADY_MEMBER");
        }
    }

    private void guardNotInvited(UUID organizationId, String email) {
        expireOverdue(organizationId);

        int open = jdbc.sql("""
                        SELECT count(*) FROM organization_invite
                        WHERE organization_id = ? AND lower(email) = ? AND state = 'open'
                        """)
                .params(organizationId, email)
                .query(Integer.class)
                .single();

        if (open > 0) {
            throw new ConflictException("This address already has an open invitation", "ALREADY_INVITED");
        }
    }

    private void expireOverdue(UUID organizationId) {
        jdbc.sql("""
                        UPDATE organization_invite SET state = 'expired'
                        WHERE organization_id = ? AND state = 'open' AND expires_at < now()
                        """)
                .param(organizationId)
                .update();
    }

    private Row require(UUID organizationId, UUID id) {
        return jdbc.sql(SELECT + " WHERE i.organization_id = ? AND i.id = ?")
                .params(organizationId, id)
                .query(InviteService::toRow)
                .optional()
                .orElseThrow(() -> NotFoundException.of("Invitation", id));
    }

    private Optional<Row> byToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return jdbc.sql("SELECT * FROM invite_lookup(?)")
                .param(SecureTokens.hash(token.trim()))
                .query(InviteService::toRow)
                .optional();
    }

    private static String effectiveState(Row row) {
        if ("open".equals(row.state()) && row.expiresAt() != null && row.expiresAt().isBefore(Instant.now())) {
            return "expired";
        }
        return row.state();
    }

    private static String codeOf(RuntimeException e) {
        if (e instanceof app.nowtask.shared.ErrorCoded coded && coded.errorCode() != null) {
            return coded.errorCode();
        }
        return "INVALID_EMAIL";
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().split("\\s+")[0];
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("The email address is required");
        }

        String cleaned = email.trim().toLowerCase(Locale.ROOT);
        if (!cleaned.matches("[^@\\s]+@[^@\\s.]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("The email address is invalid");
        }

        return cleaned;
    }

    private static Row toRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("email"),
                rs.getObject("role_id", UUID.class),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getString("state"),
                rs.getObject("invited_by", UUID.class),
                rs.getString("invited_by_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("new_requested_at") == null
                        ? null
                        : rs.getTimestamp("new_requested_at").toInstant(),
                rs.getString("organization_name"),
                rs.getString("organization_slug"),
                rs.getString("sso_domain"));
    }

    public record Accepted(UUID organizationId, UUID inviteId, String email, String roleCode) {
    }

    record Reminder(
            UUID id,
            UUID organizationId,
            String email,
            Instant expiresAt,
            String roleName,
            String invitedByName,
            String organizationName,
            String locale) {
    }

    public record Target(
            UUID organizationId,
            String organizationName,
            String email,
            String roleCode,
            String state,
            UUID userId) {

        public boolean accountExists() {
            return userId != null;
        }
    }

    record Row(
            UUID id,
            UUID organizationId,
            String email,
            UUID roleId,
            String roleCode,
            String roleName,
            String state,
            UUID invitedBy,
            String invitedByName,
            Instant createdAt,
            Instant expiresAt,
            Instant newRequestedAt,
            String organizationName,
            String organizationSlug,
            String ssoDomain) {

        InviteView toView() {
            return new InviteView(
                    id, email, roleCode, roleName, roleId,
                    "open".equals(state) && expiresAt.isBefore(Instant.now()) ? "expired" : state,
                    invitedBy, invitedByName, createdAt, expiresAt);
        }
    }
}
