package com.porganization.commitments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.commitments.recurrence.Frequency;
import com.porganization.commitments.recurrence.RecurrenceRule;
import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CommitmentRepositoryTest extends IntegrationTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 2);

    @Autowired
    private CommitmentRepository repository;

    // C01 T1 (CA1)
    @Test
    void salvaSoComTituloDataEUsuario() {
        UUID userId = UUID.randomUUID();

        Commitment salvo = repository.saveAndFlush(new Commitment(userId, "Dentista", DIA));

        Commitment lido = repository.findByIdAndUserId(salvo.getId(), userId).orElseThrow();
        assertThat(lido.getTitle()).isEqualTo("Dentista");
        assertThat(lido.getDate()).isEqualTo(DIA);
        assertThat(lido.getStartTime()).isNull();
        assertThat(lido.isDone()).isFalse();
        assertThat(lido.getRecurrenceRule()).isNull();
        assertThat(lido.getCreatedAt()).isNotNull();
    }

    // C01 T1 (CA1)
    @Test
    void semTituloFalhaPelaConstraint() {
        Commitment semTitulo = new Commitment(UUID.randomUUID(), null, DIA);

        assertThatThrownBy(() -> repository.saveAndFlush(semTitulo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // C01 T1 (CA1): título só com espaços também é recusado pelo banco
    @Test
    void tituloEmBrancoFalhaPelaConstraint() {
        Commitment emBranco = new Commitment(UUID.randomUUID(), "   ", DIA);

        assertThatThrownBy(() -> repository.saveAndFlush(emBranco))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // C01 T2 (CA2)
    @Test
    void naoDevolveCompromissosDeOutroUsuario() {
        UUID usuarioA = UUID.randomUUID();
        UUID usuarioB = UUID.randomUUID();
        Commitment deA = repository.saveAndFlush(new Commitment(usuarioA, "Aula de A", DIA));
        Commitment deB = repository.saveAndFlush(new Commitment(usuarioB, "Aula de B", DIA));

        List<Commitment> deAnoIntervalo = repository.findByUserIdAndDateBetween(usuarioA, DIA.minusDays(1), DIA.plusDays(1));

        assertThat(deAnoIntervalo).extracting(Commitment::getTitle).containsExactly("Aula de A");
        assertThat(repository.findByIdAndUserId(deB.getId(), usuarioA)).isEmpty();
        assertThat(repository.findByIdAndUserId(deA.getId(), usuarioA)).isPresent();
    }

    // A regra de recorrência é guardada em jsonb e volta igual
    @Test
    void guardaERecuperaARegraDeRecorrencia() {
        UUID userId = UUID.randomUUID();
        Commitment academia = new Commitment(userId, "Academia", DIA);
        academia.setStartTime(LocalTime.of(7, 0));
        RecurrenceRule regra = new RecurrenceRule(Frequency.WEEKLY, 1,
                List.of(WeekDay.MON, WeekDay.WED, WeekDay.FRI), LocalDate.of(2026, 12, 31), null);
        academia.setRecurrenceRule(regra);

        Commitment salvo = repository.saveAndFlush(academia);

        assertThat(repository.findByIdAndUserId(salvo.getId(), userId).orElseThrow().getRecurrenceRule())
                .isEqualTo(regra);
        String json = jdbc.queryForObject(
                "select recurrence_rule::text from commitments where id = ?", String.class, salvo.getId());
        assertThat(json).contains("\"freq\": \"WEEKLY\"").contains("\"byWeekDays\": [\"MON\", \"WED\", \"FRI\"]");
    }
}
