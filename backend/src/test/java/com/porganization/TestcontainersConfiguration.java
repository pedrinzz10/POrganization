package com.porganization;

import com.porganization.support.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Mesma versão major do Postgres do Supabase (17 em projetos novos)
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    // Relógio controlável nos testes (MutableClock.setInstant); sem ajuste, é o relógio real
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
