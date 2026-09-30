package com.porganization.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B03 T4 (CA4): toda tabela do schema public precisa de RLS ativo, senão a Data API do
 * Supabase (acessível com a anon/publishable key do frontend) consegue ler e gravar nela.
 * Roda em toda spec e barra migração nova que esqueça o "enable row level security".
 */
class RowLevelSecurityTest extends IntegrationTest {

    @Test
    void todasAsTabelasDoSchemaPublicTemRls() {
        List<String> tabelas = jdbc.queryForList("""
                select c.relname from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind in ('r', 'p')
                """, String.class);
        List<String> semRls = jdbc.queryForList("""
                select c.relname from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity
                """, String.class);

        assertThat(tabelas).contains("user_settings", "flyway_schema_history");
        assertThat(semRls).as("tabelas sem RLS").isEmpty();
    }
}
