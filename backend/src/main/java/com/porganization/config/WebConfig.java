package com.porganization.config;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration
public class WebConfig {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Mensagens de validação sempre em português, independente do Accept-Language do navegador.
    // O nome do bean precisa ser "localeResolver" para o DispatcherServlet usá-lo.
    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(PT_BR);
    }
}
