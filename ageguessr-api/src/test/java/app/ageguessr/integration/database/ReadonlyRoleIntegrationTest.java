package app.ageguessr.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * V29: the role used from a SQL client (DBeaver) reads every table, today's and the ones later
 * migrations create, and writes none. The guarantee is in PostgreSQL itself, not in the client
 * settings. One connection, so SET ROLE stays on it for the whole test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReadonlyRoleIntegrationTest {

    private static final String LATER_TABLE = "readonly_role_later_table";

    @Autowired
    private DataSource dataSource;

    private SingleConnectionDataSource connection;
    private JdbcTemplate admin;
    private JdbcTemplate readonly;

    @BeforeEach
    void connect() throws Exception {
        admin = new JdbcTemplate(dataSource);
        connection = new SingleConnectionDataSource(dataSource.getConnection(), true);
        readonly = new JdbcTemplate(connection);
    }

    @AfterEach
    void disconnect() {
        // The connection goes back to the pool: without RESET, the next test would borrow it
        // as the read-only role.
        readonly.execute("RESET ROLE");
        connection.destroy();
        admin.execute("DROP TABLE IF EXISTS " + LATER_TABLE);
    }

    @Test
    void theRoleCanLogInButHasNoPasswordAndNoPrivilege() {
        var role = admin.queryForMap(
                "SELECT rolcanlogin, rolsuper, rolcreaterole, rolcreatedb, rolpassword IS NULL AS no_password"
                        + " FROM pg_authid WHERE rolname = 'ageguessr_readonly'");

        assertThat(role)
                .containsEntry("rolcanlogin", true)
                .containsEntry("rolsuper", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("no_password", true);
    }

    @Test
    void readsTheTablesAndWritesNone() {
        readonly.execute("SET ROLE ageguessr_readonly");

        assertThat(readonly.queryForObject("SELECT count(*) FROM users", Long.class)).isNotNull();
        assertThat(readonly.queryForObject("SELECT count(*) FROM people", Long.class)).isNotNull();
        assertDenied("INSERT INTO daily_rounds (game_date, position, drawn_at) VALUES ('2999-01-01', 1, now())");
        assertDenied("UPDATE users SET username = username");
        assertDenied("DELETE FROM game_plays");
        assertDenied("TRUNCATE people");
        assertDenied("DROP TABLE hints");
        assertDenied("CREATE TABLE readonly_role_intruder (id int)");
        assertDenied("SELECT * FROM pg_authid");
    }

    @Test
    void readsEveryColumnButTheHashes() {
        readonly.execute("SET ROLE ageguessr_readonly");

        assertThat(readonly.queryForList("SELECT id, email, username, created_at FROM users")).isNotNull();
        assertThat(readonly.queryForList("SELECT user_id, expires_at FROM refresh_tokens")).isNotNull();
        assertDenied("SELECT password_hash FROM users");
        assertDenied("SELECT * FROM users");
        assertDenied("SELECT token_hash FROM refresh_tokens");
        assertDenied("SELECT token_hash FROM account_tokens");
        assertDenied("SELECT token_hash FROM email_verification_tokens");
    }

    @Test
    void readsATableALaterMigrationCreates() {
        admin.execute("CREATE TABLE " + LATER_TABLE + " (id int)");
        readonly.execute("SET ROLE ageguessr_readonly");

        assertThat(readonly.queryForObject("SELECT count(*) FROM " + LATER_TABLE, Long.class)).isZero();
        assertDenied("INSERT INTO " + LATER_TABLE + " VALUES (1)");
    }

    private void assertDenied(String sql) {
        assertThatThrownBy(() -> readonly.execute(sql))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                // DROP answers "must be owner of table", every other write "permission denied".
                .hasMessageMatching("(?s).*(permission denied|must be owner).*");
    }
}
