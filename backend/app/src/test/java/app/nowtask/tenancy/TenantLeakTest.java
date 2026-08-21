package app.nowtask.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TenantLeakTest {

    private static final UUID ORG_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID ORG_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID USER_A = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @BeforeAll
    static void twoCompanies() throws SQLException {
        try (Connection owner = TenantDatabase.asOwner()) {
            owner.setAutoCommit(true);
            seedCompany(owner, ORG_A, "firma-a", USER_A, "a@example.test", "A");
            seedCompany(owner, ORG_B, "firma-b", USER_B, "b@example.test", "B");
        }
    }

    private static void seedCompany(
            Connection owner, UUID orgId, String slug, UUID userId, String email, String marker)
            throws SQLException {

        UUID roleId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();

        run(owner, "INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending)"
                + " VALUES (?, ?, ?, ?, ?, 'admin', 0, false)",
                userId, "Osoba " + marker, "Osoba " + marker, marker, email);
        run(owner, "INSERT INTO organization (id, name, slug) VALUES (?, ?, ?)",
                orgId, "Firma " + marker, slug);
        run(owner, "INSERT INTO organization_role (id, organization_id, code, name, position, protected)"
                + " VALUES (?, ?, 'admin', 'Administrator', 0, TRUE)", roleId, orgId);
        run(owner, "INSERT INTO organization_role_permission (role_id, permission) VALUES (?, 'members.manage')",
                roleId);
        run(owner, "INSERT INTO organization_member (id, organization_id, user_id, role_id, state)"
                + " VALUES (?, ?, ?, ?, 'active')", UUID.randomUUID(), orgId, userId, roleId);
        run(owner, "INSERT INTO project (id, organization_id, name, code, position) VALUES (?, ?, ?, 'SHARED', 0)",
                projectId, orgId, "Projekt " + marker);
        run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                + " VALUES (?, ?, ?, 'todo', 'Todo', 'notStarted', 0)", statusId, orgId, projectId);
        run(owner, "INSERT INTO task (id, organization_id, project_id, task_key, title, status_id)"
                + " VALUES (?, ?, ?, 'SHARED-1', ?, ?)",
                UUID.randomUUID(), orgId, projectId, "Zadanie " + marker, statusId);
    }

    @Test
    void twoCompaniesShareAProjectCodeAndATaskKey() throws SQLException {
        assertThat(titlesVisibleTo(ORG_A)).containsExactly("Zadanie A");
        assertThat(titlesVisibleTo(ORG_B)).containsExactly("Zadanie B");
    }

    @Test
    void withoutAContextNothingIsVisible() throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, null, null);

            for (String table : List.of("task", "project", "team", "audit_event", "notification")) {
                assertThat(count(app, table))
                        .describedAs("table %s must be closed without a context", table)
                        .isZero();
            }
        }
    }

    @Test
    void aForeignOrganizationCannotBeWrittenInto() throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, USER_A.toString(), ORG_A.toString());

            assertThatThrownBy(() -> run(app,
                    "INSERT INTO team (id, organization_id, name, headcount) VALUES (?, ?, 'Podszywka', 1)",
                    UUID.randomUUID(), ORG_B))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    void aRowCannotBeMovedToAnotherOrganization() throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, USER_A.toString(), ORG_A.toString());

            assertThatThrownBy(() -> run(app, "UPDATE task SET organization_id = ?", ORG_B))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void aChildCannotPointAtAnotherCompanysParent() throws SQLException {
        UUID foreignTaskId;
        try (Connection owner = TenantDatabase.asOwner()) {
            foreignTaskId = singleId(owner, "SELECT id FROM task WHERE organization_id = '" + ORG_B + "'");
        }

        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, USER_A.toString(), ORG_A.toString());

            assertThatThrownBy(() -> run(app,
                    "INSERT INTO subtask (id, organization_id, task_id, title, position) VALUES (?, ?, ?, 'Wyciek', 0)",
                    UUID.randomUUID(), ORG_A, foreignTaskId))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("does not match parent");
        }
    }

    @Test
    void anInsertWithoutTheColumnPicksTheOrganizationFromTheContext() throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            app.setAutoCommit(false);
            TenantDatabase.enter(app, USER_B.toString(), ORG_B.toString());

            run(app, "INSERT INTO team (id, name, headcount) VALUES (?, 'Bez organizacji', 1)", UUID.randomUUID());

            assertThat(singleId(app, "SELECT organization_id FROM team WHERE name = 'Bez organizacji'"))
                    .isEqualTo(ORG_B);
            app.rollback();
        }
    }

    @Test
    void anotherCompanysMembershipIsInvisible() throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, USER_A.toString(), ORG_A.toString());

            assertThat(count(app, "organization_member")).isEqualTo(1);
            assertThat(count(app, "organization_role")).isEqualTo(1);
        }
    }

    private List<String> titlesVisibleTo(UUID organizationId) throws SQLException {
        try (Connection app = TenantDatabase.asApplication()) {
            TenantDatabase.enter(app, null, organizationId.toString());

            List<String> titles = new ArrayList<>();
            try (var statement = app.prepareStatement("SELECT title FROM task ORDER BY title");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    titles.add(rows.getString(1));
                }
            }
            return titles;
        }
    }

    private int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT count(*) FROM " + table);
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static UUID singleId(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getObject(1, UUID.class);
        }
    }

    private static void run(Connection connection, String sql, Object... params) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        }
    }
}
