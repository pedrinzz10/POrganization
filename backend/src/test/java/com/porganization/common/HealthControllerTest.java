package com.porganization.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HealthControllerTest extends IntegrationTest {

    // B02 T2 (CA2)
    @Test
    void healthRespondeUpSemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // B02 T3 (CA3)
    @Test
    void actuatorHealthEhPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // B02 T3 (CA3)
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops"})
    void actuatorNaoExpoeConfiguracao(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
    }
}
