package app.nowtask.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantSchemaTest {

    private static final Set<String> WITHOUT_ORGANIZATION_ID = Set.of(
            "flyway_schema_history",
            "app_user",
            "organization",
            "organization_role",
            "organization_role_permission",
            "organization_member",
            "organization_invite",
            "email_verification",
            "password_reset",
            "email_change",
            "user_session",
            "user_notification_pref",
            "spring_session",
            "spring_session_attributes");

    private static final Set<String> NULLABLE_ORGANIZATION_ID = Set.of("audit_event");

    private static final Set<String> IDENTITY_TABLES = Set.of(
            "organization",
            "organization_role",
            "organization_role_permission",
            "organization_member",
            "organization_invite",
            "email_verification");

    private static final Set<String> WITHOUT_ROW_LEVEL_SECURITY = Set.of(
            "flyway_schema_history",
            "app_user",
            "organization_invite",
            "email_verification",
            "password_reset",
            "email_change",
            "user_session",
            "user_notification_pref",
            "spring_session",
            "spring_session_attributes");

    @Test
    void everyDomainTableCarriesTheOrganization() throws SQLException {
        List<String> offenders = query("""
                SELECT t.table_name
                FROM information_schema.tables t
                WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
                  AND NOT EXISTS (
                      SELECT 1 FROM information_schema.columns c
                      WHERE c.table_schema = 'public' AND c.table_name = t.table_name
                        AND c.column_name = 'organization_id')
                ORDER BY 1
                """);

        offenders.removeAll(WITHOUT_ORGANIZATION_ID);
        assertThat(offenders)
                .describedAs("tables without organization_id, add the column or the allowlist entry")
                .isEmpty();
    }

    @Test
    void theOrganizationColumnIsRequired() throws SQLException {
        List<String> offenders = query("""
                SELECT c.table_name
                FROM information_schema.columns c
                WHERE c.table_schema = 'public' AND c.column_name = 'organization_id'
                  AND c.is_nullable = 'YES'
                ORDER BY 1
                """);

        offenders.removeAll(NULLABLE_ORGANIZATION_ID);
        assertThat(offenders).describedAs("organization_id has to be NOT NULL").isEmpty();
    }

    @Test
    void theOrganizationColumnDefaultsFromTheContext() throws SQLException {
        List<String> offenders = query("""
                SELECT c.table_name
                FROM information_schema.columns c
                WHERE c.table_schema = 'public' AND c.column_name = 'organization_id'
                  AND (c.column_default IS NULL OR c.column_default NOT LIKE '%app.organization_id%')
                ORDER BY 1
                """);

        offenders.removeAll(IDENTITY_TABLES);
        assertThat(offenders)
                .describedAs("an INSERT that forgets the column has to pick it up from the context")
                .isEmpty();
    }

    @Test
    void everyScopedTableHasRowLevelSecurityAndAPolicy() throws SQLException {
        List<String> withoutSecurity = query("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relrowsecurity
                ORDER BY 1
                """);
        assertThat(withoutSecurity)
                .describedAs("only the tables read before an organization is chosen may skip RLS,"
                        + " see docs/multi-tenancy.md point 4.5")
                .allMatch(WITHOUT_ROW_LEVEL_SECURITY::contains);

        List<String> withoutPolicy = query("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity
                  AND NOT EXISTS (SELECT 1 FROM pg_policies p
                                  WHERE p.schemaname = 'public' AND p.tablename = c.relname)
                ORDER BY 1
                """);

        assertThat(withoutPolicy).describedAs("RLS enabled but no policy means the table is fully closed").isEmpty();
    }

    @Test
    void theApplicationAccountCannotBypassRowLevelSecurity() throws SQLException {
        List<String> dangerous = query("""
                SELECT rolname FROM pg_roles
                WHERE rolname = 'nowtask_app' AND (rolbypassrls OR rolsuper)
                """);

        assertThat(dangerous).describedAs("nowtask_app must not bypass RLS").isEmpty();
    }

    @Test
    void theApplicationAccountOwnsNoTable() throws SQLException {
        List<String> owned = query("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_roles r ON r.oid = c.relowner
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND r.rolname = 'nowtask_app'
                ORDER BY 1
                """);

        assertThat(owned).describedAs("a table owner bypasses its own policies").isEmpty();
    }

    @Test
    void noDefinerFunctionIsExecutableByEveryone() throws SQLException {
        List<String> open = query("""
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' AND p.prosecdef
                  AND (p.proacl IS NULL
                       OR EXISTS (SELECT 1 FROM aclexplode(p.proacl) a WHERE a.grantee = 0))
                ORDER BY 1
                """);

        assertThat(open)
                .describedAs("a SECURITY DEFINER function bypasses RLS, so PUBLIC must not execute it;"
                        + " add REVOKE EXECUTE ... FROM PUBLIC")
                .isEmpty();
    }

    @Test
    void everyDefinerFunctionIsReachableByTheApplication() throws SQLException {
        List<String> unreachable = query("""
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' AND p.prosecdef
                  AND NOT has_function_privilege('nowtask_app', p.oid, 'EXECUTE')
                ORDER BY 1
                """);

        assertThat(unreachable)
                .describedAs("the application account has to be able to call the function it needs")
                .isEmpty();
    }

    @Test
    void theSchemaHasTheDefinerFunctionsWeExpect() throws SQLException {
        List<String> definers = query("""
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' AND p.prosecdef
                ORDER BY 1
                """);

        assertThat(definers)
                .describedAs("every new SECURITY DEFINER function is a deliberate hole in the isolation,"
                        + " list it here so it cannot be added quietly")
                .isSubsetOf(
                        "api_key_lookup",
                        "api_key_touch",
                        "dismiss_stale_onboarding",
                        "expire_overdue_invites",
                        "invite_lookup",
                        "invites_due_for_reminder",
                        "organization_by_sso_domain",
                        "organization_slug_taken");
    }

    private List<String> query(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = TenantDatabase.asOwner();
                var statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
