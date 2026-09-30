package com.porganization.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class InternalCronControllerIT extends NotificationsIT {

    private static final Instant HOJE_14_31 = Instant.parse("2026-10-01T17:31:00Z");
    private static final String DENTISTA_15H = "{\"title\":\"Dentista\",\"date\":\"2026-10-01\",\"startTime\":\"15:00\"}";

    // I01 T3 (CA3)
    @Test
    void semOHeaderOuComSegredoErradoResponde401() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/reminders/dispatch").header(InternalCronController.SECRET_HEADER, "errado"))
                .andExpect(status().isUnauthorized());
        // Um JWT válido não substitui o segredo
        mockMvc.perform(post("/internal/reminders/dispatch").with(usuario(userId))).andExpect(status().isUnauthorized());
    }

    @Test
    void comOSegredoRespondeQuantosEnviou() throws Exception {
        lembrete(compromisso(DENTISTA_15H), 30, ChannelType.PUSH);
        clock.setInstant(HOJE_14_31);

        mockMvc.perform(post("/internal/reminders/dispatch").header(InternalCronController.SECRET_HEADER, CRON_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    // I01 T3 (CA3)
    @Test
    void ocorrenciaConcluidaNaoDispara() throws Exception {
        String id = compromisso(DENTISTA_15H);
        lembrete(id, 30, ChannelType.PUSH);
        mockMvc.perform(patch("/api/commitments/" + id + "/done").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk());
        clock.setInstant(HOJE_14_31);

        cron();

        verify(push, never()).send(any());
    }

    @Test
    void ocorrenciaCanceladaDaSerieNaoDispara() throws Exception {
        String id = compromisso("""
                {"title":"Remédio","date":"2026-09-20","startTime":"15:00","recurrenceRule":{"freq":"DAILY","interval":1}}
                """);
        lembrete(id, 30, ChannelType.PUSH);
        mockMvc.perform(patch("/api/commitments/" + id + "/occurrences/2026-10-01").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancelled\":true}"))
                .andExpect(status().isOk());
        clock.setInstant(HOJE_14_31);

        cron();

        verify(push, never()).send(any());
    }
}
