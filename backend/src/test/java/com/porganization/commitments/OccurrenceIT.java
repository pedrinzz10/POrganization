package com.porganization.commitments;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OccurrenceIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();
    private String serieId;

    private String criar(String json) throws Exception {
        String body = mockMvc.perform(post("/api/commitments").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions patchOcorrencia(String id, String data, String json) throws Exception {
        return mockMvc.perform(patch("/api/commitments/" + id + "/occurrences/" + data).with(usuario(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions consultar(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/commitments").param("from", from).param("to", to).with(usuario(userId)));
    }

    @BeforeEach
    void serieDiaria() throws Exception {
        serieId = criar("""
                {"title":"Remédio","date":"2026-10-01","startTime":"08:00","endTime":"08:15",
                 "recurrenceRule":{"freq":"DAILY","interval":1}}
                """);
    }

    // C05 T1 (CA1)
    @Test
    void concluirUmaOcorrenciaAfetaSoAquelaData() throws Exception {
        patchOcorrencia(serieId, "2026-10-03", "{\"done\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrenceDate").value("2026-10-03"))
                .andExpect(jsonPath("$.done").value(true));

        consultar("2026-10-02", "2026-10-04")
                .andExpect(jsonPath("$[*].done").value(contains(false, true, false)));
    }

    // C05 T2 (CA2)
    @Test
    void cancelarUmaOcorrenciaTiraDaConsulta() throws Exception {
        patchOcorrencia(serieId, "2026-10-03", "{\"cancelled\":true}").andExpect(status().isOk());

        consultar("2026-10-02", "2026-10-04")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].occurrenceDate").value(contains("2026-10-02", "2026-10-04")));

        // desfazer o cancelamento traz a ocorrência de volta
        patchOcorrencia(serieId, "2026-10-03", "{\"cancelled\":false}").andExpect(status().isOk());
        consultar("2026-10-02", "2026-10-04").andExpect(jsonPath("$", hasSize(3)));
    }

    // C05 T3 (CA3)
    @Test
    void alterarOHorarioMudaSoAquelaData() throws Exception {
        patchOcorrencia(serieId, "2026-10-03", "{\"startTime\":\"18:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("18:00"))
                // a duração de 15 minutos acompanha o novo horário
                .andExpect(jsonPath("$.endTime").value("18:15"));

        consultar("2026-10-03", "2026-10-04")
                .andExpect(jsonPath("$[*].startTime").value(contains("18:00", "08:00")));
    }

    @Test
    void alterarSoOTituloDeUmaOcorrenciaMantemOsOutrosAjustes() throws Exception {
        patchOcorrencia(serieId, "2026-10-03", "{\"done\":true}").andExpect(status().isOk());
        patchOcorrencia(serieId, "2026-10-03", "{\"title\":\"Remédio (dose dupla)\"}")
                .andExpect(jsonPath("$.title").value("Remédio (dose dupla)"))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void dataQueNaoEhOcorrenciaDaSerieResponde404() throws Exception {
        patchOcorrencia(serieId, "2026-09-30", "{\"done\":true}").andExpect(status().isNotFound());
    }

    @Test
    void ocorrenciaDeOutroUsuarioResponde404() throws Exception {
        mockMvc.perform(patch("/api/commitments/" + serieId + "/occurrences/2026-10-03")
                        .with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void doneDeCompromissoUnico() throws Exception {
        String unico = criar("{\"title\":\"Dentista\",\"date\":\"2026-10-03\",\"startTime\":\"14:00\"}");

        mockMvc.perform(patch("/api/commitments/" + unico + "/done").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        consultar("2026-10-03", "2026-10-03")
                .andExpect(jsonPath("$[?(@.title == 'Dentista')].done").value(contains(true)));
    }

    @Test
    void doneDaSerieInteiraResponde400() throws Exception {
        mockMvc.perform(patch("/api/commitments/" + serieId + "/done").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ocorrenciaDeCompromissoUnicoResponde400() throws Exception {
        String unico = criar("{\"title\":\"Dentista\",\"date\":\"2026-10-03\",\"startTime\":\"14:00\"}");
        patchOcorrencia(unico, "2026-10-03", "{\"done\":true}").andExpect(status().isBadRequest());
    }
}
