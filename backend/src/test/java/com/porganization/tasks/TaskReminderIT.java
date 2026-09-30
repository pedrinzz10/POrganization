package com.porganization.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationsIT;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class TaskReminderIT extends NotificationsIT {

    // Terça 06/10/2026 em São Paulo (UTC-3)
    private static final Instant AS_14_58 = Instant.parse("2026-10-06T17:58:00Z");
    private static final Instant AS_15_04 = Instant.parse("2026-10-06T18:04:00Z");
    private static final Instant AS_15_08 = Instant.parse("2026-10-06T18:08:00Z");
    private static final Instant AS_15_12 = Instant.parse("2026-10-06T18:12:00Z");

    @BeforeEach
    void pushEmSaoPaulo() throws Exception {
        clock.setInstant(AS_14_58);
        mockMvc.perform(put("/api/settings").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"America/Sao_Paulo\",\"channels\":[\"PUSH\"]}"))
                .andExpect(status().isOk());
    }

    private String tarefa(String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // T05 T1 (CA1)
    @Test
    void avisaUmaVezNaJanelaDoHorario() throws Exception {
        mockMvc.perform(post("/api/tasks").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beber água\",\"reminderTime\":\"15:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderTime").value("15:00"));

        cron();
        verify(push, never()).send(any());

        clock.setInstant(AS_15_04);
        cron();
        ArgumentCaptor<Notification> enviado = ArgumentCaptor.forClass(Notification.class);
        verify(push, times(1)).send(enviado.capture());
        assertThat(enviado.getValue().kind()).isEqualTo(Notification.Kind.TASK_REMINDER);
        assertThat(enviado.getValue().subject()).isEqualTo("Lembrete: Beber água");
        assertThat(enviado.getValue().url()).isEqualTo("/hoje");

        clock.setInstant(AS_15_08);
        cron();
        verify(push, times(1)).send(any());
        verify(email, never()).send(any());
    }

    @Test
    void foraDaJanelaNaoAvisa() throws Exception {
        tarefa("{\"title\":\"Beber água\",\"reminderTime\":\"15:00\"}");

        clock.setInstant(AS_15_12);
        cron();

        verify(push, never()).send(any());
    }

    // T05 T2 (CA2)
    @Test
    void feitaArquivadaOuForaDoDiaNaoAvisa() throws Exception {
        String feita = tarefa("{\"title\":\"Beber água\",\"reminderTime\":\"15:00\"}");
        String arquivada = tarefa("{\"title\":\"Meditar\",\"reminderTime\":\"15:00\"}");
        tarefa("{\"title\":\"Academia\",\"weekDays\":[\"MON\",\"WED\",\"FRI\"],\"reminderTime\":\"15:00\"}");

        mockMvc.perform(put("/api/tasks/" + feita + "/completions/2026-10-06").with(usuario(userId)))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(patch("/api/tasks/" + arquivada).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk());

        clock.setInstant(AS_15_04);
        cron();

        verify(push, never()).send(any());
    }

    @Test
    void janelaNaoViraODia() {
        assertThat(TaskReminderDispatcher.inWindow(java.time.LocalTime.of(23, 55), java.time.LocalTime.of(23, 59))).isTrue();
        assertThat(TaskReminderDispatcher.inWindow(java.time.LocalTime.of(23, 55), java.time.LocalTime.of(0, 2))).isFalse();
        assertThat(TaskReminderDispatcher.inWindow(java.time.LocalTime.of(15, 0), java.time.LocalTime.of(15, 10))).isFalse();
    }
}
