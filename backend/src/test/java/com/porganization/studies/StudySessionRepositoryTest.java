package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class StudySessionRepositoryTest extends IntegrationTest {

    @Autowired
    private StudySessionRepository sessions;

    @Autowired
    private SubjectRepository subjects;

    private final UUID usuarioA = UUID.randomUUID();
    private UUID materia;

    @BeforeEach
    void materia() {
        materia = subjects.saveAndFlush(new Subject(usuarioA, "Java", 1)).getId();
    }

    private StudySession nova(UUID userId) {
        return StudySession.start(userId, materia, SessionType.LESSON, Instant.now());
    }

    // E04 T1 (CA1)
    @Test
    void soUmaSessaoAtivaPorUsuario() {
        sessions.saveAndFlush(nova(usuarioA));

        assertThatThrownBy(() -> sessions.saveAndFlush(nova(usuarioA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // E04 T1 (CA1): pausada também conta como ativa
    @Test
    void pausadaTambemBloqueiaOutraAtiva() {
        StudySession pausada = nova(usuarioA);
        pausada.pause(Instant.now());
        sessions.saveAndFlush(pausada);

        assertThatThrownBy(() -> sessions.saveAndFlush(nova(usuarioA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void terminadaNaoBloqueiaANova() {
        StudySession terminada = nova(usuarioA);
        terminada.finish(Instant.now());
        sessions.saveAndFlush(terminada);

        StudySession nova = sessions.saveAndFlush(nova(usuarioA));
        assertThat(sessions.findActive(usuarioA)).map(StudySession::getId).contains(nova.getId());
    }

    @Test
    void outroUsuarioPodeTerSuaPropriaSessaoAtiva() {
        sessions.saveAndFlush(nova(usuarioA));
        UUID usuarioB = UUID.randomUUID();
        UUID materiaDeB = subjects.saveAndFlush(new Subject(usuarioB, "Inglês", 1)).getId();

        StudySession deB = sessions.saveAndFlush(StudySession.start(usuarioB, materiaDeB, SessionType.LESSON, Instant.now()));
        assertThat(deB.getId()).isNotNull();
    }
}
