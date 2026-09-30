package com.porganization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PorganizationApplicationTests {

    // B02 T1 (CA1): o contexto sobe com o Postgres do Testcontainers
    @Test
    void contextLoads() {
    }
}
