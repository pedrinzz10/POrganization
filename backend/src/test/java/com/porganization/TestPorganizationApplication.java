package com.porganization;

import org.springframework.boot.SpringApplication;

// Sobe a API localmente com um Postgres do Testcontainers: ./mvnw spring-boot:test-run
// Sem Supabase: só as rotas públicas funcionam, a menos que SUPABASE_* venha do ambiente.
public class TestPorganizationApplication {

    public static void main(String[] args) {
        defaultIfAbsent("SUPABASE_JWKS_URI", "http://localhost:54321/auth/v1/.well-known/jwks.json");
        defaultIfAbsent("SUPABASE_ISSUER", "http://localhost:54321/auth/v1");
        SpringApplication.from(PorganizationApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

    private static void defaultIfAbsent(String name, String value) {
        if (System.getenv(name) == null && System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
    }
}
