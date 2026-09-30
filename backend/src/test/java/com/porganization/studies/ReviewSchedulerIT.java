package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.studies.fsrs.Fsrs;
import com.porganization.studies.fsrs.FsrsCard;
import com.porganization.studies.fsrs.FsrsParameters;
import com.porganization.studies.fsrs.ReviewGrade;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReviewSchedulerIT extends IntegrationTest {

    private static final String BASE = "/api/study/sessions";

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID java;

    @BeforeEach
    void materia() {
        java = subjects.saveAndFlush(new Subject(userId, "Java", 1)).getId();
    }

    private ResultActions acao(String caminho, String json) throws Exception {
        return mockMvc.perform(post(caminho).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Estuda uma aula de "minutos" minutos terminando em "fim" e devolve o id da Lesson criada. */
    private String estudarAula(Instant inicio, int minutos) throws Exception {
        clock.setInstant(inicio);
        String sessao = id(acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"}"));
        clock.setInstant(inicio.plusSeconds(minutos * 60L));
        String body = acao(BASE + "/" + sessao + "/finish", "{\"title\":\"Streams\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.lessonId");
    }

    private Map<String, Object> item(String lessonId) {
        return jdbc.queryForMap("select * from review_items where lesson_id = ?::uuid", lessonId);
    }

    // E07 T1 (CA1)
    @Test
    void aulaDe50MinGeraRevisaoDe25MinParaAmanha() throws Exception {
        // 10:00 em São Paulo (13:00 UTC) de 01/10
        String licao = estudarAula(Instant.parse("2026-10-01T12:10:00Z"), 50);

        Map<String, Object> item = item(licao);
        assertThat(item.get("due_date").toString()).isEqualTo("2026-10-02");
        assertThat(item.get("review_minutes")).isEqualTo(25);
        assertThat(item.get("reps")).isEqualTo(0);
        assertThat(item.get("fsrs_state")).isEqualTo("NEW");
    }

    // E07 T1 (CA1): "amanhã" é no fuso do usuário
    @Test
    void aulaTerminadaAs23h30EmSaoPauloVenceNoDiaSeguinteLocal() throws Exception {
        // termina 02:30 UTC de 02/10 = 23:30 de 01/10 em São Paulo
        String licao = estudarAula(Instant.parse("2026-10-02T01:40:00Z"), 50);

        assertThat(item(licao).get("due_date").toString()).isEqualTo("2026-10-02");
    }

    // E07 T2 (CA2)
    @Test
    void revisaoComNotaReagendaPeloFsrs() throws Exception {
        String licao = estudarAula(Instant.parse("2026-10-01T12:10:00Z"), 50);

        clock.setInstant(Instant.parse("2026-10-02T12:00:00Z"));
        String revisao = id(acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"REVIEW\",\"lessonId\":\"" + licao + "\"}")
                .andExpect(jsonPath("$.plannedMinutes").value(25)));
        clock.setInstant(Instant.parse("2026-10-02T12:25:00Z"));
        acao(BASE + "/" + revisao + "/finish", "{\"grade\":\"OK\"}").andExpect(status().isOk());

        FsrsCard esperado = new Fsrs(FsrsParameters.defaults()).review(FsrsCard.newCard(), ReviewGrade.OK, LocalDate.of(2026, 10, 2));
        Map<String, Object> item = item(licao);
        assertThat(item.get("due_date").toString()).isEqualTo(LocalDate.of(2026, 10, 2).plusDays(esperado.scheduledDays()).toString());
        assertThat(item.get("reps")).isEqualTo(1);
        assertThat(item.get("last_grade")).isEqualTo("OK");
        assertThat(item.get("fsrs_state")).isEqualTo("REVIEW");
        assertThat((Double) item.get("stability")).isEqualTo(esperado.stability());
    }

    // E07 T2 (CA2)
    @Test
    void revisaoSemNotaResponde400() throws Exception {
        String licao = estudarAula(Instant.parse("2026-10-01T12:10:00Z"), 50);
        clock.setInstant(Instant.parse("2026-10-02T12:00:00Z"));
        String revisao = id(acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"REVIEW\",\"lessonId\":\"" + licao + "\"}"));

        acao(BASE + "/" + revisao + "/finish", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("grade"));

        // a sessão continua ativa para o usuário escolher a nota
        assertThat(jdbc.queryForObject("select status from study_sessions where id = ?::uuid", String.class, revisao))
                .isEqualTo("RUNNING");
    }

    @Test
    void segundaRevisaoUsaOEstadoDaPrimeira() throws Exception {
        String licao = estudarAula(Instant.parse("2026-10-01T12:10:00Z"), 50);
        Fsrs fsrs = new Fsrs(FsrsParameters.defaults());
        FsrsCard esperado = fsrs.review(FsrsCard.newCard(), ReviewGrade.OK, LocalDate.of(2026, 10, 2));
        esperado = fsrs.review(esperado, ReviewGrade.FACIL, esperado.due());

        revisar(licao, Instant.parse("2026-10-02T12:00:00Z"), "OK");
        revisar(licao, Instant.parse("2026-10-05T12:00:00Z"), "FACIL");

        Map<String, Object> item = item(licao);
        assertThat(item.get("reps")).isEqualTo(2);
        assertThat(item.get("due_date").toString()).isEqualTo(esperado.due().toString());
    }

    private void revisar(String licao, Instant quando, String nota) throws Exception {
        clock.setInstant(quando);
        String revisao = id(acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"REVIEW\",\"lessonId\":\"" + licao + "\"}"));
        clock.setInstant(quando.plusSeconds(20 * 60));
        acao(BASE + "/" + revisao + "/finish", "{\"grade\":\"" + nota + "\"}").andExpect(status().isOk());
    }
}
