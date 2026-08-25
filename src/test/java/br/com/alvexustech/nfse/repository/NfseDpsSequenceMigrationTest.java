package br.com.alvexustech.nfse.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class NfseDpsSequenceMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nfse")
            .withUsername("nfse")
            .withPassword("nfse");

    @Test
    void createsSequenceTableWithRequestedFiscalColumnsAndUniqueIssuerSerial() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {

            assertThat(column(statement, "nfse_dps_sequence", "id")).containsExactly("uuid", "NO", null);
            assertThat(column(statement, "nfse_dps_sequence", "issuer_cnpj")).containsExactly("character varying", "NO", 14);
            assertThat(numericColumn(statement, "nfse_dps_sequence", "dps_serial")).containsExactly("numeric", "NO", 5, 0);
            assertThat(numericColumn(statement, "nfse_dps_sequence", "last_dps_issued")).containsExactly("numeric", "NO", 15, 0);

            assertThat(uniqueConstraint(statement)).isEqualTo("uq_nfse_dps_sequence_issuer_serial");
            assertThat(uniqueConstraintColumns(statement)).containsExactly("issuer_cnpj", "dps_serial");

            assertThat(numericColumn(statement, "nfse_emission", "dps_serial")).containsExactly("numeric", "YES", 5, 0);
            assertThat(numericColumn(statement, "nfse_emission", "dps_number")).containsExactly("numeric", "YES", 15, 0);
        }
    }

    private Object[] column(Statement statement, String table, String column) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_name = '%s' AND column_name = '%s'
                """.formatted(table, column))) {
            assertThat(resultSet.next()).isTrue();
            return new Object[]{resultSet.getString("data_type"), resultSet.getString("is_nullable"),
                    (Integer) resultSet.getObject("character_maximum_length")};
        }
    }

    private Object[] numericColumn(Statement statement, String table, String column) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT data_type, is_nullable, numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_name = '%s' AND column_name = '%s'
                """.formatted(table, column))) {
            assertThat(resultSet.next()).isTrue();
            return new Object[]{resultSet.getString("data_type"), resultSet.getString("is_nullable"),
                    resultSet.getInt("numeric_precision"), resultSet.getInt("numeric_scale")};
        }
    }

    private String uniqueConstraint(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_name = 'nfse_dps_sequence' AND constraint_type = 'UNIQUE'
                """)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("constraint_name");
        }
    }

    private java.util.List<String> uniqueConstraintColumns(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                WHERE tc.table_name = 'nfse_dps_sequence'
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY kcu.ordinal_position
                """)) {
            java.util.List<String> columns = new java.util.ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            return columns;
        }
    }
}
