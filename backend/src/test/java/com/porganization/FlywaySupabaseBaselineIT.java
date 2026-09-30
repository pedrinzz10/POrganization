package com.porganization;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Projeto novo do Supabase já vem com a função public.rls_auto_enable ("RLS automático"). Com o
 * baseline na versão 0 (application.yml), o Flyway aceita o schema não vazio e ainda aplica todas
 * as migrações a partir da V1.
 */
class FlywaySupabaseBaselineIT {

    @Test
    void schemaPublicComFuncaoDoSupabaseRecebeTodasAsMigracoes() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    Statement st = c.createStatement()) {
                st.execute("create function public.rls_auto_enable() returns void language sql as 'select 1'");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();
            flyway.migrate();

            MigrationInfo[] applied = flyway.info().applied();
            assertThat(applied[0].getVersion().getVersion()).isEqualTo("0");
            assertThat(applied).extracting(i -> i.getVersion().getVersion()).contains("1", "22");
            try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("select count(*) from information_schema.tables where table_name = 'user_settings'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
