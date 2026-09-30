package com.porganization;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class PorganizationApplicationTests extends IntegrationTest {

    @Autowired
    private Environment environment;

    // B02 T1 (CA1) e B03 T3 (CA3): o contexto sobe com todas as migrações e o
    // Hibernate validando as entidades contra o esquema (sem SchemaManagementException)
    @Test
    void contextoSobeValidandoOEsquema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
