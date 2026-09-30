package com.porganization.today;

import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.finance.today.FinanceToday;
import com.porganization.studies.DailyStudyPlanner;
import java.time.LocalDate;
import java.util.List;

/**
 * Tudo o que a tela Hoje mostra, numa chamada só.
 *
 * @param date        hoje no fuso do usuário
 * @param timezone    fuso usado para decidir o "hoje"
 * @param commitments ocorrências de hoje, em ordem cronológica
 * @param studies     plano de estudo do dia: revisões vencidas primeiro, depois as aulas
 * @param finance     o que vence em breve, orçamentos em alerta e o gasto do dia
 */
public record TodayResponse(LocalDate date, String timezone, List<OccurrenceResponse> commitments,
        DailyStudyPlanner.Plan studies, FinanceToday finance) {
}
