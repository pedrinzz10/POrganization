package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationsIT;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class ScheduledNoticeIT extends NotificationsIT {

    // Horários em São Paulo (UTC-3), quarta 07/10/2026 (5º dia útil)
    private static final Instant AS_08_55 = Instant.parse("2026-10-07T11:55:00Z");
    private static final Instant AS_09_02 = Instant.parse("2026-10-07T12:02:00Z");
    private static final Instant AS_09_07 = Instant.parse("2026-10-07T12:07:00Z");

    private String conta;

    @BeforeEach
    void avisoAsNove() throws Exception {
        jdbc.update("delete from recurring_transactions");
        mockMvc.perform(put("/api/settings").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"America/Sao_Paulo\",\"channels\":[\"PUSH\"],\"scheduledNoticeTime\":\"09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledNoticeTime").value("09:00"));
        conta = id(post("/api/finance/accounts"), "{\"name\":\"Bradesco\",\"type\":\"CHECKING\",\"initialBalance\":\"0.00\"}");
    }

    private String id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
        String body = mockMvc.perform(request.with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String categoria(String nome) throws Exception {
        String body = mockMvc.perform(get("/api/finance/categories").with(usuario(userId))).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.name == '" + nome + "')].id");
        return ids.getFirst();
    }

    private void agendado(String descricao, String tipo, String categoria, String regra) throws Exception {
        id(post("/api/finance/recurring"), """
                {"type":"%s","amount":"100.00","description":"%s","accountId":"%s","categoryId":"%s","startMonth":"2026-10",%s}
                """.formatted(tipo, descricao, conta, categoria(categoria), regra));
    }

    // F20 T3 (CA3)
    @Test
    void umAvisoPorDiaComOsItensParaConfirmar() throws Exception {
        agendado("Salário", "INCOME", "Salário", "\"ruleType\":\"BUSINESS_DAY\",\"businessDay\":5"); // hoje
        agendado("Internet", "EXPENSE", "Moradia", "\"dayOfMonth\":5");                               // segunda: atrasada
        agendado("Aluguel", "EXPENSE", "Moradia", "\"dayOfMonth\":20");                               // futura: fora

        clock.setInstant(AS_08_55);
        cron();
        verify(push, never()).send(any());

        clock.setInstant(AS_09_02);
        cron();
        ArgumentCaptor<Notification> enviado = ArgumentCaptor.forClass(Notification.class);
        verify(push, times(1)).send(enviado.capture());
        assertThat(enviado.getValue().kind()).isEqualTo(Notification.Kind.SCHEDULED_NOTICE);
        assertThat(enviado.getValue().subject()).isEqualTo("Para confirmar: 2 agendados");
        assertThat(enviado.getValue().lines()).containsExactly(
                "Internet · R$ 100,00 · Bradesco (atrasado)",
                "Salário · R$ 100,00 · Bradesco");
        assertThat(enviado.getValue().url()).isEqualTo("/financas/agendados");

        clock.setInstant(AS_09_07);
        cron();
        verify(push, times(1)).send(any());
    }

    @Test
    void semItensParaConfirmarNaoManda() throws Exception {
        agendado("Aluguel", "EXPENSE", "Moradia", "\"dayOfMonth\":20");

        clock.setInstant(AS_09_02);
        cron();

        verify(push, never()).send(any());
    }

    @Test
    void telaHojeTrazOsItensParaConfirmarSemDuplicarEmVencimentos() throws Exception {
        agendado("Internet", "EXPENSE", "Moradia", "\"dayOfMonth\":7");
        clock.setInstant(AS_09_02);

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.finance.toConfirm[0].description").value("Internet"))
                .andExpect(jsonPath("$.finance.toConfirm[0].status").value("TO_CONFIRM"))
                .andExpect(jsonPath("$.finance.dueSoon.length()").value(0));
    }
}
