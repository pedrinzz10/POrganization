package com.porganization.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.finance.today.FinanceToday;
import com.porganization.finance.today.FinanceToday.DueItem;
import com.porganization.finance.today.FinanceToday.DueKind;
import com.porganization.studies.DailyStudyPlanner.Plan;
import com.porganization.studies.DailyStudyPlanner.ReviewSuggestion;
import com.porganization.today.TodayResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyDigestServiceTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-10-10");
    private final UUID userId = UUID.randomUUID();

    private static OccurrenceResponse compromisso(String titulo, LocalTime hora, boolean feito) {
        return new OccurrenceResponse(UUID.randomUUID(), HOJE, titulo, hora, null, hora == null, feito, false, null, null);
    }

    private static ReviewSuggestion revisao(String aula) {
        return new ReviewSuggestion(UUID.randomUUID(), UUID.randomUUID(), "Java", aula, HOJE, 0, 10);
    }

    private static TodayResponse dia(List<OccurrenceResponse> compromissos, List<ReviewSuggestion> revisoes, List<DueItem> vencimentos) {
        return new TodayResponse(HOJE, "America/Sao_Paulo", compromissos, new Plan(revisoes, List.of()),
                new FinanceToday(vencimentos, List.of(), BigDecimal.ZERO, List.of(), null));
    }

    // I05 T2 (CA2)
    @Test
    void textoTrazAsContagensDoDia() {
        TodayResponse t = dia(
                List.of(compromisso("Dentista", LocalTime.of(15, 0), false), compromisso("Aniversário", null, false)),
                List.of(revisao("Aula 1"), revisao("Aula 2"), revisao("Aula 3")),
                List.of(new DueItem(DueKind.STATEMENT, UUID.randomUUID(), "Fatura Nubank", LocalDate.parse("2026-10-12"),
                        new BigDecimal("1450.00"), UUID.randomUUID(), YearMonth.of(2026, 10))));

        Notification resumo = DailyDigestService.compose(userId, t).orElseThrow();

        assertThat(resumo.subject()).isEqualTo("Seu dia: 2 compromissos, 3 revisões, 1 vencimento")
                .contains("2 compromissos").contains("3 revisões").contains("1 vencimento");
        assertThat(resumo.kind()).isEqualTo(Notification.Kind.DAILY_DIGEST);
        assertThat(resumo.url()).isEqualTo("/hoje");
        assertThat(resumo.lines()).containsExactly(
                "15:00 · Dentista",
                "Dia todo · Aniversário",
                "Revisar Aula 1 (Java)",
                "Revisar Aula 2 (Java)",
                "Revisar Aula 3 (Java)",
                "Fatura Nubank: R$ 1.450,00 vence 12/10");
    }

    @Test
    void singularEConcluidosNaoContam() {
        TodayResponse t = dia(
                List.of(compromisso("Reunião", LocalTime.of(9, 0), false), compromisso("Feito", LocalTime.of(8, 0), true)),
                List.of(revisao("Aula 1")), List.of());

        assertThat(DailyDigestService.compose(userId, t).orElseThrow().subject())
                .isEqualTo("Seu dia: 1 compromisso, 1 revisão, 0 vencimentos");
    }

    @Test
    void diaVazioNaoGeraResumo() {
        assertThat(DailyDigestService.compose(userId, dia(List.of(compromisso("Feito", LocalTime.NOON, true)), List.of(), List.of())))
                .isEmpty();
    }
}
