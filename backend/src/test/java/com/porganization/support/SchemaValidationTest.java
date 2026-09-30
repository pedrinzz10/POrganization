package com.porganization.support;

import static org.assertj.core.api.Assertions.assertThat;

import fixtures.schema.DivergentUserSettings;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * B03 T5 (CA3): prova que ddl-auto=validate barra entidade divergente da tabela.
 * A entidade de teste fica fora de com.porganization para não entrar no contexto da aplicação.
 */
class SchemaValidationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void entidadeDivergenteDaTabelaImpedeASubida() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withUserConfiguration(DivergentEntityConfig.class)
                .withPropertyValues(
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.jpa.hibernate.ddl-auto=validate")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(SchemaManagementException.class)
                            .hasMessageContaining("coluna_inexistente");
                });
    }

    // Sem @Configuration: aninhada com ela, o TestContext a usaria como configuração do teste
    @EntityScan(basePackageClasses = DivergentUserSettings.class)
    static class DivergentEntityConfig {
    }
}
