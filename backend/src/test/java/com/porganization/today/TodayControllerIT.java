package com.porganization.today;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.studies.Subject;
import com.porganization.studies.SubjectRepository;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class TodayControllerIT extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();

    private void criar(String json) throws Exception {
        mockMvc.perform(post("/api/commitments").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // C10 T1 (CA1)
    @Test
    void usaODiaNoFusoDoUsuario() throws Exception {
        criar("{\"title\":\"Reunião\",\"date\":\"2026-10-01\",\"startTime\":\"15:00\"}");
        criar("{\"title\":\"Aniversário\",\"date\":\"2026-10-01\"}");
        criar("{\"title\":\"Amanhã\",\"date\":\"2026-10-02\",\"startTime\":\"09:00\"}");

        // 02:30 em UTC é 23:30 do dia 1º em São Paulo (fuso padrão, sem user_settings)
        clock.setInstant(Instant.parse("2026-10-02T02:30:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.commitments", hasSize(2)))
                .andExpect(jsonPath("$.commitments[*].title").value(contains("Aniversário", "Reunião")));
    }

    @Test
    void respeitaOFusoConfiguradoPeloUsuario() throws Exception {
        jdbc.update("insert into user_settings (user_id, timezone) values (?, 'Europe/Lisbon')", userId);
        criar("{\"title\":\"Em Lisboa\",\"date\":\"2026-10-02\",\"startTime\":\"10:00\"}");

        // mesmo instante: em Lisboa já são 03:30 do dia 2
        clock.setInstant(Instant.parse("2026-10-02T02:30:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.date").value("2026-10-02"))
                .andExpect(jsonPath("$.commitments[*].title").value(contains("Em Lisboa")));
    }

    @Test
    void incluiOcorrenciasDeCompromissosRecorrentes() throws Exception {
        criar("""
                {"title":"Remédio","date":"2026-09-01","startTime":"08:00","recurrenceRule":{"freq":"DAILY"}}
                """);
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.commitments[0].title").value("Remédio"))
                .andExpect(jsonPath("$.commitments[0].occurrenceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.commitments[0].recurring").value(true));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/today")).andExpect(status().isUnauthorized());
    }

    // E11 T1 (CA1)
    @Test
    void incluiOPlanoDeEstudosNaOrdemDoPlanner() throws Exception {
        Subject java = subjects.saveAndFlush(new Subject(userId, "Java", 1));
        java.setSessionsPerWeek(1);
        subjects.saveAndFlush(java);
        subjects.saveAndFlush(new Subject(userId, "Inglês", 2));

        // aula de Java na segunda 28/09 (cumpre a meta de 1) -> revisão vence 29/09
        clock.setInstant(Instant.parse("2026-09-28T12:00:00Z"));
        String body = mockMvc.perform(post("/api/study/sessions").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + java.getId() + "\",\"type\":\"LESSON\"}"))
                .andReturn().getResponse().getContentAsString();
        String sessao = JsonPath.read(body, "$.id");
        clock.setInstant(Instant.parse("2026-09-28T12:50:00Z"));
        mockMvc.perform(post("/api/study/sessions/" + sessao + "/finish").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Streams\"}"))
                .andExpect(status().isOk());

        // quinta 01/10: 1 revisão vencida (2 dias de atraso) e 1 matéria pendente (Inglês)
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.studies.reviews", hasSize(1)))
                .andExpect(jsonPath("$.studies.reviews[0].lessonTitle").value("Streams"))
                .andExpect(jsonPath("$.studies.reviews[0].daysOverdue").value(2))
                .andExpect(jsonPath("$.studies.lessons", hasSize(1)))
                .andExpect(jsonPath("$.studies.lessons[0].subjectName").value("Inglês"));
    }

    // ---------- finanças (F16) ----------

    private String postId(String url, String json) throws Exception {
        String body = mockMvc.perform(post(url).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String categoria(String nome) throws Exception {
        String body = mockMvc.perform(get("/api/finance/categories").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(body, "$[?(@.name == '" + nome + "')].id");
        return ids.getFirst();
    }

    // F16 T1 (CA1)
    @Test
    void faturaQueVenceEmDoisDiasApareceEmDueSoonAtePagar() throws Exception {
        clock.setInstant(Instant.parse("2026-10-10T15:00:00Z"));
        String conta = postId("/api/finance/accounts", "{\"name\":\"Corrente\",\"type\":\"CHECKING\",\"initialBalance\":\"1000.00\"}");
        String cartao = postId("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        // Compra em 01/10, antes do fechamento (5): fatura que vence em 12/10
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"450.00\",\"date\":\"2026-10-01\",\"categoryId\":\"%s\"}".formatted(categoria("Lazer"))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.finance.dueSoon", hasSize(1)))
                .andExpect(jsonPath("$.finance.dueSoon[0].kind").value("STATEMENT"))
                .andExpect(jsonPath("$.finance.dueSoon[0].title").value("Fatura Nubank"))
                .andExpect(jsonPath("$.finance.dueSoon[0].dueDate").value("2026-10-12"))
                .andExpect(jsonPath("$.finance.dueSoon[0].amount").value("450.00"))
                .andExpect(jsonPath("$.finance.dueSoon[0].referenceMonth").value("2026-10"))
                .andReturn().getResponse().getContentAsString();
        String fatura = JsonPath.read(body, "$.finance.dueSoon[0].id");

        mockMvc.perform(post("/api/finance/cards/statements/" + fatura + "/pay").with(usuario(userId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.finance.dueSoon", hasSize(0)));
    }

    @Test
    void fixoQueVenceNaJanelaAtravessandoOMesEOrcamentoEstourado() throws Exception {
        // 30/09: a janela vai até 03/10, então o fixo do dia 2 de outubro já precisa ser gerado
        clock.setInstant(Instant.parse("2026-09-30T15:00:00Z"));
        String conta = postId("/api/finance/accounts", "{\"name\":\"Corrente\",\"type\":\"CHECKING\",\"initialBalance\":\"0.00\"}");
        String lazer = categoria("Lazer");
        postId("/api/finance/recurring", """
                {"type":"EXPENSE","amount":"1500.00","description":"Aluguel","accountId":"%s","categoryId":"%s",
                 "dayOfMonth":2,"startMonth":"2026-09"}
                """.formatted(conta, categoria("Moradia")));
        postId("/api/finance/budgets", "{\"categoryId\":\"%s\",\"amount\":\"100.00\"}".formatted(lazer));
        postId("/api/finance/transactions", """
                {"type":"EXPENSE","amount":"120.00","date":"2026-09-30","description":"Show","accountId":"%s","categoryId":"%s","paid":true}
                """.formatted(conta, lazer));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.finance.dueSoon", hasSize(1)))
                .andExpect(jsonPath("$.finance.dueSoon[0].kind").value("BILL"))
                .andExpect(jsonPath("$.finance.dueSoon[0].title").value("Aluguel"))
                .andExpect(jsonPath("$.finance.dueSoon[0].dueDate").value("2026-10-02"))
                .andExpect(jsonPath("$.finance.budgetAlerts", hasSize(1)))
                .andExpect(jsonPath("$.finance.budgetAlerts[0].categoryName").value("Lazer"))
                .andExpect(jsonPath("$.finance.budgetAlerts[0].level").value("ESTOURADO"))
                .andExpect(jsonPath("$.finance.spentToday").value("120.00"));
    }
}
