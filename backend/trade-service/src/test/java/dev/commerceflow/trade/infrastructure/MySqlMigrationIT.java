package dev.commerceflow.trade.infrastructure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers // No disabledWithoutDocker: an explicit integration run must fail without Docker.
class MySqlMigrationIT {
    private static final String MIGRATOR_PASSWORD = password();
    private static final String APP_PASSWORD = password();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestImages.locked("MYSQL_IMAGE", "mysql"))
        .withDatabaseName("cf_bootstrap")
        .withUsername("tc_bootstrap")
        .withPassword(password())
        .withEnv("MYSQL_ROOT_HOST", "%")
        .withEnv("TRADE_MIGRATION_PASSWORD", MIGRATOR_PASSWORD)
        .withEnv("TRADE_APP_PASSWORD", APP_PASSWORD)
        .withCopyFileToContainer(MountableFile.forHostPath(
            TestImages.repositoryRoot().resolve("deploy/mysql/init/10-create-trade-users.sh")),
            "/docker-entrypoint-initdb.d/10-create-trade-users.sh")
        .withStartupTimeout(Duration.ofMinutes(3));

    private String schema;
    @TempDir Path temporaryMigrations;

    @BeforeEach
    void allocateIsolatedSchema() throws SQLException {
        schema = "cf_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = rootConnection(MYSQL.getDatabaseName()); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
        }
    }

    @AfterEach
    void removeOnlyThisTestsSchema() throws SQLException {
        if (schema != null && schema.matches("cf_test_[a-f0-9]{32}")) {
            try (var connection = rootConnection(MYSQL.getDatabaseName()); var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    @Test
    void emptyDatabaseMigratesAndSecondRunPreservesData() throws SQLException {
        var flyway = migrations(schema, "root", MYSQL.getPassword());
        int pending = flyway.info().pending().length;
        assertThat(pending).isPositive();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(pending);
        try (var connection = rootConnection(schema)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM app_user")).isZero();
            insertFixture(connection, "test-user");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM app_user")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")).isEqualTo(pending);
        }
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void realUniqueIndexRejectsDuplicatesAndTransactionRollbackRemovesFixture() throws SQLException {
        migrations(schema, "root", MYSQL.getPassword()).migrate();
        try (var connection = rootConnection(schema)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'app_user' AND index_name = 'uk_app_user_login_name' AND non_unique = 0")).isEqualTo(1);
            connection.setAutoCommit(false);
            try {
                insertFixture(connection, "same-login");
                assertThatThrownBy(() -> insertFixture(connection, "same-login"))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            assertThat(count(connection, "SELECT COUNT(*) FROM app_user")).isZero();
        }
    }

    @Test
    void changedMigrationIsRejectedWithoutApplyingTheChangedSql() throws Exception {
        var flyway = migrations(schema, "root", MYSQL.getPassword());
        flyway.migrate();
        try (var migrations = Files.list(TestImages.repositoryRoot().resolve("backend/trade-service/src/main/resources/db/migration"))) {
            for (var file : migrations.toList()) Files.copy(file, temporaryMigrations.resolve(file.getFileName()));
        }
        try (var original = getClass().getResourceAsStream("/db/migration/V1__create_app_user.sql")) {
            assertThat(original).isNotNull();
            String changedSql = new String(original.readAllBytes(), StandardCharsets.UTF_8)
                + "\nALTER TABLE app_user ADD COLUMN forbidden_probe INT;\n";
            Files.writeString(temporaryMigrations.resolve("V1__create_app_user.sql"), changedSql);
        }
        var changed = Flyway.configure().dataSource(url(schema), "root", MYSQL.getPassword())
            .locations("filesystem:" + temporaryMigrations).cleanDisabled(true).load();
        assertThatThrownBy(changed::validate).isInstanceOf(FlywayException.class).hasMessageContaining("checksum mismatch");
        try (var connection = rootConnection(schema)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'forbidden_probe'")).isZero();
        }
    }

    @Test
    void cleanIsDisabledAndExistingDataSurvives() throws SQLException {
        var flyway = migrations(schema, "root", MYSQL.getPassword());
        flyway.migrate();
        try (var connection = rootConnection(schema)) {
            insertFixture(connection, "keep-me");
            assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class).hasMessageContaining("cleanDisabled");
            assertThat(count(connection, "SELECT COUNT(*) FROM app_user")).isEqualTo(1);
        }
    }

    @Test
    void wrongPasswordFailsWithoutCreatingTables() throws SQLException {
        var wrongCredentials = migrations(schema, "root", password());
        assertThatThrownBy(wrongCredentials::migrate).isInstanceOf(FlywayException.class);
        try (var connection = rootConnection(schema)) {
            assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")).isZero();
        }
    }

    @Test
    void composeInitializationSeparatesMigrationAndApplicationPrivileges() throws SQLException {
        String bootstrap = MYSQL.getDatabaseName();
        try (var foreign = rootConnection(schema); var statement = foreign.createStatement()) {
            statement.execute("CREATE TABLE foreign_probe (id BIGINT)");
        }
        migrations(bootstrap, "trade_migrator", MIGRATOR_PASSWORD).migrate();
        try (var application = DriverManager.getConnection(url(bootstrap), "trade_app", APP_PASSWORD)) {
            insertFixture(application, "runtime-fixture");
            assertThat(count(application, "SELECT COUNT(*) FROM app_user")).isEqualTo(1);
            assertDenied(application, "CREATE TABLE denied_probe (id BIGINT)");
            assertDenied(application, "SELECT * FROM " + schema + ".foreign_probe");
            assertThat(count(application, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'denied_probe'")).isZero();
        }
    }

    private static Flyway migrations(String database, String user, String secret) {
        return Flyway.configure().dataSource(url(database), user, secret)
            .locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false)
            .validateMigrationNaming(true).connectRetries(0).load();
    }

    private static String url(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database
            + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000";
    }

    private static Connection rootConnection(String database) throws SQLException {
        return DriverManager.getConnection(url(database), "root", MYSQL.getPassword());
    }

    private static void insertFixture(Connection connection, String login) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO app_user(login_name, password_hash) VALUES (?, ?)")) {
            statement.setString(1, login);
            statement.setString(2, "test-only-not-a-login-credential");
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void assertDenied(Connection connection, String sql) {
        assertThatThrownBy(() -> {
            try (var statement = connection.createStatement()) { statement.execute(sql); }
        }).isInstanceOf(SQLException.class).satisfies(error ->
            assertThat(((SQLException) error).getErrorCode()).isIn(1044, 1142));
    }

    private static String password() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
