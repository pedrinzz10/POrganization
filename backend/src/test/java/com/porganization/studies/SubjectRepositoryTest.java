package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class SubjectRepositoryTest extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private TagRepository tags;

    @Autowired
    private TransactionTemplate tx;

    private Subject materia(UUID userId, int sessoes, int minutos) {
        Subject subject = new Subject(userId, "Java Advanced", 1);
        subject.setSessionsPerWeek(sessoes);
        subject.setLessonMinutes(minutos);
        return subject;
    }

    // E01 T2 (CA2)
    @ParameterizedTest(name = "sessões {0}, minutos {1}")
    @CsvSource({ "22, 50", "-1, 50", "2, 3", "2, 241" })
    void limitesForaDaFaixaFalhamPelaConstraint(int sessoes, int minutos) {
        assertThatThrownBy(() -> subjects.saveAndFlush(materia(UUID.randomUUID(), sessoes, minutos)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // E01 T2 (CA2): os extremos da faixa são aceitos
    @ParameterizedTest(name = "sessões {0}, minutos {1}")
    @CsvSource({ "0, 5", "21, 240" })
    void limitesDaFaixaSaoAceitos(int sessoes, int minutos) {
        assertThat(subjects.saveAndFlush(materia(UUID.randomUUID(), sessoes, minutos)).getId()).isNotNull();
    }

    @Test
    void padroesDaMateria() {
        Subject salva = subjects.saveAndFlush(new Subject(UUID.randomUUID(), "Inglês", 1));
        assertThat(salva.getLessonMinutes()).isEqualTo(50);
        assertThat(salva.getSessionsPerWeek()).isEqualTo(2);
        assertThat(salva.isArchived()).isFalse();
    }

    @Test
    void guardaAsTagsDaMateria() {
        UUID dono = UUID.randomUUID();
        Tag faculdade = tags.saveAndFlush(new Tag(dono, "faculdade"));
        Subject java = materia(dono, 2, 50);
        java.getTags().add(faculdade);
        UUID id = subjects.saveAndFlush(java).getId();

        tx.executeWithoutResult(status -> assertThat(subjects.findByIdAndUserId(id, dono).orElseThrow().getTags())
                .extracting(Tag::getName).containsExactly("faculdade"));
        assertThat(subjects.findByIdAndUserId(id, UUID.randomUUID())).isEmpty();
    }
}
