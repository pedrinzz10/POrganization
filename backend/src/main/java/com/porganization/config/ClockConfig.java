package com.porganization.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Agora" vem sempre deste bean, nunca de LocalDate.now() direto: assim os testes controlam
 * a hora (MutableClock) e regras como "hoje no fuso do usuário" ficam testáveis.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
