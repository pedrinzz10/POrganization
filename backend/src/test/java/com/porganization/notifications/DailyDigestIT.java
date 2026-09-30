package com.porganization.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class DailyDigestIT extends NotificationsIT {

    // Horários em São Paulo (UTC-3)
    private static final Instant AS_06_55 = Instant.parse("2026-10-01T09:55:00Z");
    private static final Instant AS_07_02 = Instant.parse("2026-10-01T10:02:00Z");
    private static final Instant AS_07_07 = Instant.parse("2026-10-01T10:07:00Z");

    @BeforeEach
    void resumoAsSete() throws Exception {
        mockMvc.perform(put("/api/settings").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"America/Sao_Paulo\",\"channels\":[\"PUSH\"],\"digestTime\":\"07:00\"}"))
                .andExpect(status().isOk());
        compromisso("{\"title\":\"Dentista\",\"date\":\"2026-10-01\",\"startTime\":\"15:00\"}");
    }

    private static Notification resumo() {
        return argThat(n -> n != null && n.kind() == Notification.Kind.DAILY_DIGEST);
    }

    // I05 T1 (CA1)
    @Test
    void primeiroCronDepoisDasSeteEnviaUmaVezSo() throws Exception {
        clock.setInstant(AS_06_55);
        cron();
        verify(push, never()).send(any());

        clock.setInstant(AS_07_02);
        cron();
        ArgumentCaptor<Notification> enviado = ArgumentCaptor.forClass(Notification.class);
        verify(push, times(1)).send(enviado.capture());
        assertThat(enviado.getValue().subject()).isEqualTo("Seu dia: 1 compromisso, 0 revisões, 0 vencimentos");
        assertThat(enviado.getValue().lines()).containsExactly("15:00 · Dentista");

        clock.setInstant(AS_07_07);
        cron();
        verify(push, times(1)).send(resumo());
    }

    @Test
    void noDiaSeguinteSaiDeNovo() throws Exception {
        compromisso("{\"title\":\"Academia\",\"date\":\"2026-10-02\",\"startTime\":\"18:00\"}");
        clock.setInstant(AS_07_02);
        cron();
        clock.setInstant(AS_07_02.plusSeconds(24 * 3600));
        cron();

        verify(push, times(2)).send(resumo());
    }

    @Test
    void seNenhumCanalEntregaOProximoCronTentaDeNovo() throws Exception {
        doThrow(new IllegalStateException("push fora do ar")).doNothing().when(push).send(any());
        clock.setInstant(AS_07_02);
        cron();
        clock.setInstant(AS_07_07);
        cron();

        verify(push, times(2)).send(resumo());
        assertThat(jdbc.queryForObject("select last_digest_date::text from user_settings where user_id = ?", String.class, userId))
                .isEqualTo("2026-10-01");
    }
}
