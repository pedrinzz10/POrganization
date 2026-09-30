package com.porganization;

import org.springframework.boot.SpringApplication;

// Sobe a API localmente com um Postgres do Testcontainers: ./mvnw spring-boot:test-run
public class TestPorganizationApplication {

    public static void main(String[] args) {
        SpringApplication.from(PorganizationApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
