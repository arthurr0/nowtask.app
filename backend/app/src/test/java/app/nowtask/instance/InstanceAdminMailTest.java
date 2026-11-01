package app.nowtask.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import app.nowtask.identity.EmailVerificationService;
import app.nowtask.identity.InstanceAdminGrants;

@SpringBootTest
class InstanceAdminMailTest {

    private static final String SEEDED = "seeded-admin@example.test";
    private static final String SIGNED_UP = "signed-up-admin@example.test";

    private static final InstanceTestDatabase DB = new InstanceTestDatabase();

    static {
        try {
            DB.insertUser(SEEDED, "active", false);
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
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("nowtask.instance.admins", () -> SEEDED + "," + SIGNED_UP);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstanceAdminGrants grants;

    @Autowired
    private EmailVerificationService verifications;

    @Test
    void anUnverifiedAccountIsNotPromotedUntilItsAddressIsVerified() throws Exception {
        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", SEEDED))
                .isEqualTo("false");

        grants.grantListed();

        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", SEEDED))
                .isEqualTo("false");

        verifications.markVerified(DB.userId(SEEDED));

        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", SEEDED))
                .isEqualTo("true");
    }

    @Test
    void aListedSignupWaitsForVerificationWhenMailIsConfigured() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Signed Up\",\"email\":\"" + SIGNED_UP
                                + "\",\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.instanceAdmin").value(false));

        UUID id = DB.userId(SIGNED_UP);
        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE id = ?", id)).isEqualTo("false");

        verifications.markVerified(id);

        assertThat(DB.scalar("SELECT instance_admin::text FROM app_user WHERE id = ?", id)).isEqualTo("true");
    }
}
