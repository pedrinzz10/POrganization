package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class StudySessionControllerIT extends IntegrationTest {

    private static final String BASE = "/api/study/sessions";

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID java;

    @BeforeEach
    void materia() {
        Subject subject = new Subject(userId, "Java", 1);
        subject.setLessonMinutes(45);
        java = subjects.saveAndFlush(subject).getId();
    }

    private void em(String hora) {
        clock.setInstant(Instant.parse("2026-10-01T" + hora + ":00Z"));
    }

    private ResultActions acao(String caminho, String json) throws Exception {
        return mockMvc.perform(post(caminho).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String iniciar() throws Exception {
        String body = acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // E06 T1 (CA1)
    @Test
    void pausarERetomarAcumulaOTempoPausado() throws Exception {
        em("10:00");
        String id = iniciar();
        em("10:20");
        acao(BASE + "/" + id + "/pause", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        em("10:25");
        acao(BASE + "/" + id + "/resume", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        em("10:55");

        acao(BASE + "/" + id + "/finish", "{\"title\":\"Streams\",\"notes\":\"map, filter, collect\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.pausedSeconds").value(300))
                .andExpect(jsonPath("$.elapsedSeconds").value(3000));

        assertThat(jdbc.queryForObject("select paused_seconds from study_sessions where id = ?::uuid", Integer.class, id))
                .isEqualTo(300);
    }

    // E06 T2 (CA2)
    @Test
    void iniciarComOutraSessaoAtivaResponde409() throws Exception {
        em("10:00");
        iniciar();

        acao(BASE, "{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    // E06 T3 (CA3)
    @Test
    void terminarAulaCriaALicaoComADuracaoEfetiva() throws Exception {
        em("10:00");
        String id = iniciar();
        em("10:20");
        acao(BASE + "/" + id + "/pause", "{}");
        em("10:25");
        acao(BASE + "/" + id + "/resume", "{}");
        em("10:55");

        String body = acao(BASE + "/" + id + "/finish", "{\"title\":\"Streams\",\"notes\":\"map, filter\"}")
                .andReturn().getResponse().getContentAsString();

        String lessonId = JsonPath.read(body, "$.lessonId");
        Map<String, Object> licao = jdbc.queryForMap("select title, notes, duration_minutes, subject_id from lessons where id = ?::uuid", lessonId);
        assertThat(licao).containsEntry("title", "Streams").containsEntry("notes", "map, filter")
                .containsEntry("duration_minutes", 50).containsEntry("subject_id", java);
    }

    @Test
    void sessaoAtivaVoltaDepoisDeRecarregarComOTempoCorreto() throws Exception {
        em("10:00");
        String id = iniciar();
        em("10:12");

        mockMvc.perform(get(BASE + "/active").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.elapsedSeconds").value(720))
                .andExpect(jsonPath("$.plannedMinutes").value(45))
                .andExpect(jsonPath("$.subjectName").value("Java"));
    }

    @Test
    void semSessaoAtivaResponde204() throws Exception {
        mockMvc.perform(get(BASE + "/active").with(usuario(userId))).andExpect(status().isNoContent());
    }

    @Test
    void depoisDeTerminarNaoHaSessaoAtivaENovaPodeComecar() throws Exception {
        em("10:00");
        String id = iniciar();
        em("10:30");
        acao(BASE + "/" + id + "/abandon", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABANDONED"));

        mockMvc.perform(get(BASE + "/active").with(usuario(userId))).andExpect(status().isNoContent());
        iniciar();
    }

    @Test
    void transicaoInvalidaResponde409() throws Exception {
        em("10:00");
        String id = iniciar();
        acao(BASE + "/" + id + "/resume", "{}").andExpect(status().isConflict());
    }

    @Test
    void terminarAulaSemTituloResponde400() throws Exception {
        em("10:00");
        String id = iniciar();
        acao(BASE + "/" + id + "/finish", "{\"notes\":\"sem título\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void sessaoOuMateriaDeOutroUsuarioResponde404() throws Exception {
        em("10:00");
        String id = iniciar();
        mockMvc.perform(post(BASE + "/" + id + "/pause").with(usuario(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE).with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"}"))
                .andExpect(status().isNotFound());
    }
}
