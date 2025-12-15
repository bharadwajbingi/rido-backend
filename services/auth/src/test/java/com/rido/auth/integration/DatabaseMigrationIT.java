package com.rido.auth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database migrations.
 * 
 * Verifies that Flyway migrations execute correctly and create the expected schema.
 */
@DisplayName("Database Migration Integration Tests")
public class DatabaseMigrationIT extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should create auth schema")
    void shouldCreateAuthSchema() {
        List<Map<String, Object>> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'auth'"
        );

        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).get("schema_name")).isEqualTo("auth");
    }

    @Test
    @DisplayName("Should create users table with correct columns")
    void shouldCreateUsersTable() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = 'auth' AND table_name = 'users' " +
                        "ORDER BY ordinal_position"
        );

        assertThat(columns).isNotEmpty();

        // Verify key columns exist
        assertThat(columns).extracting(col -> col.get("column_name"))
                .contains("id", "username", "password_hash", "role", "created_at", "locked_until");

        // Verify username is NOT NULL
        Map<String, Object> usernameColumn = columns.stream()
                .filter(col -> "username".equals(col.get("column_name")))
                .findFirst()
                .orElseThrow();

        assertThat(usernameColumn.get("is_nullable")).isEqualTo("NO");
    }

    @Test
    @DisplayName("Should create refresh_tokens table with correct columns")
    void shouldCreateRefreshTokensTable() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'auth' AND table_name = 'refresh_tokens'"
        );

        assertThat(columns).extracting(col -> col.get("column_name"))
                .contains("id", "user_id", "token_hash", "expires_at", "revoked",
                        "device_id", "ip", "user_agent", "jti", "created_at", "rotated");
    }

    @Test
    @DisplayName("Should create audit_logs table with correct columns")
    void shouldCreateAuditLogsTable() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'auth' AND table_name = 'audit_logs'"
        );

        assertThat(columns).extracting(col -> col.get("column_name"))
                .contains("id", "entity", "entity_id", "action", "actor", "metadata",
                        "created_at", "device_id", "event_type", "failure_reason",
                        "ip_address", "success", "timestamp", "user_agent", "user_id", "username");
    }

    @Test
    @DisplayName("Should create required indexes on audit_logs")
    void shouldCreateAuditLogIndexes() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                        "WHERE schemaname = 'auth' AND tablename = 'audit_logs'"
        );

        assertThat(indexes).extracting(idx -> idx.get("indexname"))
                .contains("idx_audit_event_type", "idx_audit_user_id", 
                         "idx_audit_timestamp", "idx_audit_username");
    }

    @Test
    @DisplayName("Should create unique constraint on users.username")
    void shouldCreateUsernameUniqueConstraint() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name, constraint_type " +
                        "FROM information_schema.table_constraints " +
                        "WHERE table_schema = 'auth' AND table_name = 'users' " +
                        "AND constraint_type = 'UNIQUE'"
        );

        assertThat(constraints).extracting(c -> c.get("constraint_name"))
                .contains("users_username_key");
    }

    @Test
    @DisplayName("Should enable pgcrypto extension")
    void shouldEnablePgcryptoExtension() {
        List<Map<String, Object>> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname = 'pgcrypto'"
        );

        assertThat(extensions).hasSize(1);
    }

    @Test
    @DisplayName("Should enable uuid-ossp extension")
    void shouldEnableUuidOsspExtension() {
        List<Map<String, Object>> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname = 'uuid-ossp'"
        );

        assertThat(extensions).hasSize(1);
    }

    @Test
    @DisplayName("Should set correct primary keys")
    void shouldSetPrimaryKeys() {
        // Check users table primary key
        List<Map<String, Object>> usersPk = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                        "WHERE table_schema = 'auth' AND table_name = 'users' " +
                        "AND constraint_type = 'PRIMARY KEY'"
        );
        assertThat(usersPk).hasSize(1);

        // Check refresh_tokens table primary key
        List<Map<String, Object>> tokensPk = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                        "WHERE table_schema = 'auth' AND table_name = 'refresh_tokens' " +
                        "AND constraint_type = 'PRIMARY KEY'"
        );
        assertThat(tokensPk).hasSize(1);

        // Check audit_logs table primary key
        List<Map<String, Object>> auditPk = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                        "WHERE table_schema = 'auth' AND table_name = 'audit_logs' " +
                        "AND constraint_type = 'PRIMARY KEY'"
        );
        assertThat(auditPk).hasSize(1);
    }
}
