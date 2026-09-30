package com.porganization.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlywayMigrationTest extends IntegrationTest {

    // B03 T1 (CA1)
    @Test
    void aplicaV1ECriaUserSettings() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);
        assertThat(success).isTrue();

        Integer tabelas = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'user_settings'
                """, Integer.class);
        assertThat(tabelas).isEqualTo(1);
    }
}
