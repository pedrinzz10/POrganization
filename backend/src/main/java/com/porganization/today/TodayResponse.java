package com.porganization.today;

import com.porganization.commitments.dto.OccurrenceResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Tudo o que a tela Hoje mostra, numa chamada só. As etapas 3 e 4 acrescentam estudos e finanças.
 *
 * @param date        hoje no fuso do usuário
 * @param timezone    fuso usado para decidir o "hoje"
 * @param commitments ocorrências de hoje, em ordem cronológica
 */
public record TodayResponse(LocalDate date, String timezone, List<OccurrenceResponse> commitments) {
}
