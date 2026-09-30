package com.porganization.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Dinheiro trafega como string decimal com 2 casas ("1000.10"), nunca como número: o JSON
 * number vira float em JavaScript e perde centavos. O Jackson 3 serializa BigDecimal como número
 * por padrão; este módulo muda isso num único ponto. Na entrada, string ou número são aceitos.
 */
@Configuration
public class JacksonConfig {

    @Bean
    SimpleModule moneyModule() {
        SimpleModule module = new SimpleModule("money");
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
            }
        });
        return module;
    }
}
