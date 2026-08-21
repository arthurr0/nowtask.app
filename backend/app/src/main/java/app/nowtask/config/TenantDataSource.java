package app.nowtask.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;

class TenantDataSource extends DelegatingDataSource {

    private static final String APPLY = "SELECT set_config('app.user_id', ?, false),"
            + " set_config('app.organization_id', ?, false)";

    TenantDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return apply(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return apply(super.getConnection(username, password));
    }

    private Connection apply(Connection connection) throws SQLException {
        OrganizationContext context = OrganizationContextHolder.currentOrNull();
        String userId = context == null || context.userId() == null ? "" : context.userId().toString();
        String organizationId =
                context == null || context.organizationId() == null ? "" : context.organizationId().toString();

        try (PreparedStatement statement = connection.prepareStatement(APPLY)) {
            statement.setString(1, userId);
            statement.setString(2, organizationId);
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }

        return connection;
    }
}
