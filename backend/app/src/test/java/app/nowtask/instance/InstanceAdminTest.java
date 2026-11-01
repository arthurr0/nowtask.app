package app.nowtask.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import app.nowtask.identity.InstanceAdminGrants;
import app.nowtask.identity.api.Announcements;
import app.nowtask.realtime.RealtimeBroker;
import com.jayway.jsonpath.JsonPath;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
class InstanceAdminTest {

    private static final String ENV_ADMIN = "env-admin@example.test";
    private static final String LATE_ADMIN = "late-admin@example.test";

    private static final InstanceTestDatabase DB = new InstanceTestDatabase();

    static {
        try {
            DB.insertUser(ENV_ADMIN, "active", true);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::jdbcUrl);
        registry.add("spring.datasource.username", () -> "nowtask_app");
        registry.add("spring.datasource.password", () -> InstanceTestDatabase.APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("nowtask.instance.admins", () -> "Env-Admin@Example.test, " + LATE_ADMIN);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Announcements announcements;

    @Autowired
    private InstanceAdminGrants grants;

    @Autowired
    private RealtimeBroker broker;

    private MockMvc mvc;

    @BeforeEach
    void reset() throws SQLException {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        DB.execute("DELETE FROM instance_audit_event");
        DB.execute("UPDATE instance_settings SET signup_mode = NULL, announcement_text = NULL,"
                + " announcement_level = NULL, announcement_until = NULL, updated_by = NULL");
        DB.execute("UPDATE app_user SET instance_admin = FALSE WHERE email <> ?", ENV_ADMIN);
        DB.execute("UPDATE app_user SET state = 'active' WHERE email = ?", ENV_ADMIN);
    }

    @Test
    void theConfiguredAddressIsGrantedAtStartup() throws SQLException {
        assertThat(grants).isInstanceOf(ApplicationRunner.class);
        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", ENV_ADMIN)).isEqualTo("true");
    }

    @Test
    void anAccountRegisteredLaterWithAListedAddressIsGranted() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Late Admin\",\"email\":\"" + LATE_ADMIN
                                + "\",\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.instanceAdmin").value(true));

        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", LATE_ADMIN))
                .isEqualTo("true");
    }

    @Test
    void theCurrentUserCarriesTheFlag() throws Exception {
        String regular = DB.createUser("flag-regular");

        mvc.perform(get("/api/auth/me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(true));
        mvc.perform(get("/api/auth/me").with(user(regular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(false));
    }

    @Test
    void anonymousIsRejectedAndAnOrdinaryUserGets403() throws Exception {
        String regular = DB.createUser("plain");

        mvc.perform(get("/api/instance/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/instance/overview").with(user(regular))).andExpect(status().isForbidden());
        mvc.perform(get("/api/instance/users").with(user(regular))).andExpect(status().isForbidden());
        mvc.perform(post("/api/instance/mail/test").with(user(regular)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApiKeyGets403EvenWhenItsOwnerIsAnInstanceAdmin() throws Exception {
        UUID organization = DB.createOrganization("key-org");
        UUID role = DB.createRole(organization);
        String token = DB.createApiKey(organization, role, ENV_ADMIN);

        mvc.perform(get("/api/instance/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");
    }

    @Test
    void anAdminWithoutAnyOrganizationReadsTheOverview() throws Exception {
        assertThat(DB.scalar("SELECT count(*)::text FROM organization_member m JOIN app_user u ON u.id = m.user_id"
                + " WHERE u.email = ?", ENV_ADMIN)).isEqualTo("0");

        mvc.perform(get("/api/instance/overview").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.schemaVersion").value(org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andExpect(jsonPath("$.counts.users").isNumber())
                .andExpect(jsonPath("$.counts.instanceAdmins").value(1))
                .andExpect(jsonPath("$.signupMode.effective").value("open"))
                .andExpect(jsonPath("$.signupMode.source").value("env"))
                .andExpect(jsonPath("$.health.database").value("up"))
                .andExpect(jsonPath("$.health.mail").value("not_configured"))
                .andExpect(jsonPath("$.health.github").value("not_configured"));

        mvc.perform(get("/api/instance/users").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void suspendAndRestoreChangeTheStateAndWriteTheAudit() throws Exception {
        UUID organization = DB.createOrganization("suspend-me");

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("suspended");
        assertThat(DB.scalar("SELECT (suspended_at IS NOT NULL)::text FROM organization WHERE id = ?", organization))
                .isEqualTo("true");

        mvc.perform(get("/api/instance/organizations?state=suspended&q=suspend-me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("suspend-me"))
                .andExpect(jsonPath("$.items[0].state").value("suspended"));

        mvc.perform(post("/api/instance/organizations/" + organization + "/restore")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");

        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.suspend'"
                + " AND target_id = ? AND actor_id = (SELECT id FROM app_user WHERE email = ?)",
                organization.toString(), ENV_ADMIN)).isEqualTo("1");
        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.restore'"
                + " AND target_id = ?", organization.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/audit").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].action").value("organization.restore"))
                .andExpect(jsonPath("$.items[0].actor.name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].targetType").value("organization"));
    }

    @Test
    void deletingNeedsTheMatchingSlug() throws Exception {
        UUID organization = DB.createOrganization("delete-me");

        mvc.perform(delete("/api/instance/organizations/" + organization)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"something-else\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SLUG_MISMATCH"));

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");

        mvc.perform(delete("/api/instance/organizations/" + organization)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"delete-me\"}"))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("deleted");
        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.delete'"
                + " AND target_id = ?", organization.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/organizations?q=delete-me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anUnknownOrganizationIs404() throws Exception {
        mvc.perform(post("/api/instance/organizations/" + UUID.randomUUID() + "/suspend")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingAUserClosesTheirSessions() throws Exception {
        String victim = DB.createUser("victim");
        UUID victimId = UUID.fromString(DB.scalar("SELECT id::text FROM app_user WHERE email = ?", victim));

        DB.execute("INSERT INTO user_session (id, user_id, session_key) VALUES (?, ?, ?)",
                UUID.randomUUID(), victimId, "key-" + victimId);
        DB.execute("INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,"
                + " max_inactive_interval, expiry_time, principal_name) VALUES (?, ?, 0, 0, 100, 9999999999999, ?)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), victim);

        mvc.perform(post("/api/instance/users/" + victimId + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT state FROM app_user WHERE id = ?", victimId)).isEqualTo("disabled");
        assertThat(DB.scalar("SELECT (revoked_at IS NOT NULL)::text FROM user_session WHERE user_id = ?", victimId))
                .isEqualTo("true");
        assertThat(DB.scalar("SELECT count(*)::text FROM spring_session WHERE principal_name = ?", victim))
                .isEqualTo("0");
        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'user.disable'"
                + " AND target_id = ?", victimId.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/users?state=disabled&q=victim").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("disabled"))
                .andExpect(jsonPath("$.items[0].email").value(victim));

        mvc.perform(post("/api/instance/users/" + victimId + "/enable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT state FROM app_user WHERE id = ?", victimId)).isEqualTo("active");

        mvc.perform(post("/api/instance/users/" + victimId + "/sessions/revoke")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        UUID self = UUID.fromString(DB.scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(post("/api/instance/users/" + self + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"));

        assertThat(DB.scalar("SELECT state FROM app_user WHERE id = ?", self)).isEqualTo("active");
    }

    @Test
    void theLastInstanceAdminCannotBeRevoked() throws Exception {
        UUID self = UUID.fromString(DB.scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(patch("/api/instance/users/" + self)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_INSTANCE_ADMIN"));

        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE id = ?", self)).isEqualTo("true");
    }

    @Test
    void grantingAndRevokingAnotherAdminWorksButNotOnYourself() throws Exception {
        String colleague = DB.createUser("colleague");
        UUID colleagueId = UUID.fromString(DB.scalar("SELECT id::text FROM app_user WHERE email = ?", colleague));
        UUID self = UUID.fromString(DB.scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(patch("/api/instance/users/" + colleagueId)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(true))
                .andExpect(jsonPath("$.email").value(colleague));

        mvc.perform(patch("/api/instance/users/" + self)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"));

        mvc.perform(patch("/api/instance/users/" + colleagueId)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(false));

        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action IN"
                + " ('user.instance_admin_grant', 'user.instance_admin_revoke')")).isEqualTo("2");
    }

    @Test
    void theSignupModeOverrideDecidesWhoCanRegister() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":\"invite-only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupMode").value("invite-only"))
                .andExpect(jsonPath("$.effectiveSignupMode").value("invite-only"))
                .andExpect(jsonPath("$.envSignupMode").value("open"))
                .andExpect(jsonPath("$.updatedBy.name").isNotEmpty());

        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Closed Door\",\"email\":\"closed-door@example.test\","
                                + "\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SIGNUP_INVITE_ONLY"));

        mvc.perform(get("/api/instance/overview").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.signupMode.effective").value("invite-only"))
                .andExpect(jsonPath("$.signupMode.source").value("settings"));

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupMode").isEmpty())
                .andExpect(jsonPath("$.effectiveSignupMode").value("open"));

        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Open Door\",\"email\":\"open-door@example.test\","
                                + "\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated());

        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'settings.update'"))
                .isEqualTo("2");
    }

    @Test
    void anInvalidSignupModeIsRejected() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":\"whenever\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAnnouncementIsActiveUntilItExpires() throws Exception {
        assertThat(announcements.active()).isNull();

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"Maintenance tonight\",\"level\":\"warning\","
                                + "\"until\":\"" + Instant.now().plusSeconds(3600) + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement.text").value("Maintenance tonight"))
                .andExpect(jsonPath("$.announcement.level").value("warning"));

        assertThat(announcements.active()).isNotNull();
        assertThat(announcements.active().text()).isEqualTo("Maintenance tonight");

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"Old news\",\"level\":\"info\","
                                + "\"until\":\"" + Instant.now().minusSeconds(60) + "\"}}"))
                .andExpect(status().isOk());

        assertThat(announcements.active()).isNull();

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement").isEmpty());
    }

    @Test
    void anAnnouncementLongerThan500CharactersIs422() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"" + "x".repeat(501) + "\",\"level\":\"info\"}}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"" + "x".repeat(500) + "\",\"level\":\"info\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void theMailTestAnswers503WhenMailIsNotConfigured() throws Exception {
        mvc.perform(post("/api/instance/mail/test")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
    }

    @Test
    void anAdminWhoIsDisabledOrRevokedLosesAccessOnTheNextRequest() throws Exception {
        String second = DB.createUser("second-admin");
        DB.execute("UPDATE app_user SET instance_admin = TRUE WHERE email = ?", second);

        mvc.perform(get("/api/instance/overview").with(user(second))).andExpect(status().isOk());

        DB.execute("UPDATE app_user SET state = 'disabled' WHERE email = ?", second);
        mvc.perform(get("/api/instance/overview").with(user(second))).andExpect(status().isForbidden());

        DB.execute("UPDATE app_user SET state = 'active', instance_admin = FALSE WHERE email = ?", second);
        mvc.perform(get("/api/instance/overview").with(user(second))).andExpect(status().isForbidden());
    }

    @Test
    void theApiKeyOfADisabledUserIsRejectedUntilTheAccountIsEnabled() throws Exception {
        UUID organization = DB.createOrganization("key-owner-org");
        UUID role = DB.createRole(organization);
        String owner = DB.createUser("key-owner");
        DB.addMember(organization, role, owner);
        String token = DB.createApiKey(organization, role, owner);
        UUID ownerId = DB.userId(owner);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/instance/users/" + ownerId + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/instance/users/" + ownerId + "/enable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aMutatingCallWithoutTheCsrfTokenIs403() throws Exception {
        UUID organization = DB.createOrganization("no-csrf");

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend").with(user(ENV_ADMIN)))
                .andExpect(status().isForbidden());

        assertThat(DB.scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");
    }

    @Test
    void theMembersAndKeysOfASuspendedOrganizationAreCutOffAndRestored() throws Exception {
        UUID organization = DB.createOrganization("cut-off");
        UUID role = DB.createRole(organization);
        String member = DB.createUser("cut-off-member");
        DB.addMember(organization, role, member);
        String token = DB.createApiKey(organization, role, member);

        mvc.perform(get("/api/organization/members").with(user(member))).andExpect(status().isOk());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/organization/members").with(user(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ORGANIZATION"));
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/instance/organizations/" + organization + "/restore")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/organization/members").with(user(member))).andExpect(status().isOk());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void aStaleOrganizationHeaderDoesNotBlockTheInstanceApi() throws Exception {
        UUID organization = DB.createOrganization("stale-header");
        UUID role = DB.createRole(organization);
        String admin = DB.createUser("stale-admin");
        DB.addMember(organization, role, admin);
        DB.execute("UPDATE app_user SET instance_admin = TRUE WHERE email = ?", admin);

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(admin))
                        .header("X-Org-Id", organization.toString())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/instance/overview")
                        .with(user(admin))
                        .header("X-Org-Id", organization.toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/organization/members")
                        .with(user(admin))
                        .header("X-Org-Id", organization.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theSearchTextMatchesPercentAndUnderscoreLiterally() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        DB.createOrganization("wild-a-" + tag, "Acme 100% Co " + tag);
        DB.createOrganization("wild-b-" + tag, "Acme 100X Co " + tag);
        DB.createOrganization("wild-c-" + tag, "Under_score " + tag);
        DB.createOrganization("wild-d-" + tag, "UnderXscore " + tag);

        mvc.perform(get("/api/instance/organizations").param("q", "100% Co " + tag).with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("wild-a-" + tag));
        mvc.perform(get("/api/instance/organizations").param("q", "er_sc").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.items[?(@.slug == 'wild-c-" + tag + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.slug == 'wild-d-" + tag + "')]").isEmpty());
        mvc.perform(get("/api/instance/organizations").param("q", "%").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.items[?(@.slug == 'wild-b-" + tag + "')]").isEmpty());
    }

    @Test
    void pagingReportsTheTotalAndClampsTheSize() throws Exception {
        String tag = "pg" + UUID.randomUUID().toString().substring(0, 8);
        for (int index = 0; index < 3; index++) {
            DB.createOrganization(tag + "-" + index, "Paged " + tag + " " + index);
        }

        mvc.perform(get("/api/instance/organizations").param("q", tag).param("size", "2").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/instance/organizations")
                        .param("q", tag).param("size", "2").param("page", "1").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/instance/organizations")
                        .param("q", tag).param("size", "2").param("page", "9").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/instance/organizations").param("size", "1000").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.size").value(200));
        mvc.perform(get("/api/instance/organizations").param("size", "0").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void deletedOrganizationsAndUsersAreNeitherListedNorCounted() throws Exception {
        long organizations = overviewCount("organizations");
        long users = overviewCount("users");

        UUID organization = DB.createOrganization("counted-org");
        String person = DB.createUser("counted-user");
        DB.execute("UPDATE app_user SET instance_admin = TRUE WHERE email = ?", person);

        assertThat(overviewCount("organizations")).isEqualTo(organizations + 1);
        assertThat(overviewCount("users")).isEqualTo(users + 1);
        assertThat(overviewCount("instanceAdmins")).isEqualTo(2);

        DB.execute("UPDATE app_user SET state = 'disabled' WHERE email = ?", person);
        assertThat(overviewCount("instanceAdmins")).isEqualTo(1);

        mvc.perform(delete("/api/instance/organizations/" + organization)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"counted-org\"}"))
                .andExpect(status().isNoContent());
        DB.execute("UPDATE app_user SET state = 'deleted' WHERE email = ?", person);

        assertThat(overviewCount("organizations")).isEqualTo(organizations);
        assertThat(overviewCount("users")).isEqualTo(users);

        mvc.perform(get("/api/instance/users").param("q", person).with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/instance/organizations").param("q", "counted-org").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void noOpMutationsWriteNoAuditRow() throws Exception {
        UUID organization = DB.createOrganization("noop-org");
        String person = DB.createUser("noop-user");
        UUID personId = DB.userId(person);

        mvc.perform(post("/api/instance/organizations/" + organization + "/restore")
                        .with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/instance/users/" + personId + "/enable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event")).isEqualTo("0");

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/instance/users/" + personId + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/instance/users/" + personId + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(DB.scalar("SELECT count(*)::text FROM instance_audit_event")).isEqualTo("2");
    }

    @Test
    void bootstrapCarriesTheActiveAnnouncement() throws Exception {
        UUID organization = DB.createOrganization("bootstrap-org");
        UUID role = DB.createRole(organization);
        String member = DB.createUser("bootstrap-member");
        DB.addMember(organization, role, member);

        mvc.perform(get("/api/bootstrap").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement").isEmpty())
                .andExpect(jsonPath("$.currentUser.instanceAdmin").value(false));

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"Heads up\",\"level\":\"critical\"}}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/bootstrap").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement.text").value("Heads up"))
                .andExpect(jsonPath("$.announcement.level").value("critical"))
                .andExpect(jsonPath("$.announcement.until").isEmpty());
    }

    @Test
    void openStreamsAreClosedWhenAnOrganizationIsSuspendedOrAUserIsDisabled() throws Exception {
        UUID organization = DB.createOrganization("stream-org");
        UUID other = DB.createOrganization("stream-other");
        String first = DB.createUser("stream-first");
        String second = DB.createUser("stream-second");

        SseEmitter suspended = broker.open(organization, DB.userId(first));
        SseEmitter untouched = broker.open(other, DB.userId(second));
        SseEmitter ofUser = broker.open(other, DB.userId(first));

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> suspended.send("x")).isInstanceOf(IllegalStateException.class);
        untouched.send("x");
        ofUser.send("x");

        mvc.perform(post("/api/instance/users/" + DB.userId(first) + "/disable")
                        .with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> ofUser.send("x")).isInstanceOf(IllegalStateException.class);
        untouched.send("x");
    }

    private long overviewCount(String field) throws Exception {
        String body = mvc.perform(get("/api/instance/overview").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) JsonPath.read(body, "$.counts." + field)).longValue();
    }
}
