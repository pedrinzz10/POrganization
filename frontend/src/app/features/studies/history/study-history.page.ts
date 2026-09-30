import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/http/problem';
import { addDays, parseIsoDate, today, weekRange } from '../../commitments/data/date-range.util';
import { StudiesService } from '../data/studies.service';
import { LessonEntry } from '../data/study.model';

/** "2h05", "45 min" */
export function formatMinutes(total: number): string {
  if (total < 60) {
    return `${total} min`;
  }
  return `${Math.floor(total / 60)}h${String(total % 60).padStart(2, '0')}`;
}

const SEMANAS = 8;

/**
 * Histórico de estudo das últimas 8 semanas. Os dados chegam por rxResource: o template lê
 * isLoading(), error() e value() direto como signals, sem subscribe manual.
 */
@Component({
  selector: 'app-study-history-page',
  imports: [DatePipe, MatProgressBarModule, MatExpansionModule, MatButtonModule],
  templateUrl: './study-history.page.html',
  styleUrl: './study-history.page.scss',
})
export class StudyHistoryPage {
  private readonly studies = inject(StudiesService);

  private readonly semanaAtual = weekRange(today());
  protected readonly periodo = { from: addDays(this.semanaAtual.from, -7 * (SEMANAS - 1)), to: this.semanaAtual.to };

  protected readonly stats = rxResource({
    params: () => this.periodo,
    stream: ({ params }) => this.studies.stats(params.from, params.to),
  });

  protected readonly erro = computed(() =>
    this.stats.error() ? problemMessage(this.stats.error(), 'Não foi possível carregar o histórico.') : null,
  );

  protected readonly formatar = formatMinutes;
  protected readonly parse = parseIsoDate;

  /** Altura de cada barra em % da semana com mais minutos. */
  protected readonly barras = computed(() => {
    const semanas = this.stats.hasValue() ? this.stats.value().weeks : [];
    const maximo = Math.max(1, ...semanas.map((w) => w.minutes));
    return semanas.map((w) => ({ ...w, altura: Math.round((w.minutes / maximo) * 100) }));
  });

  protected readonly aulasPorMateria = computed(() => {
    const porMateria = new Map<string, LessonEntry[]>();
    for (const aula of this.stats.hasValue() ? this.stats.value().lessons : []) {
      porMateria.set(aula.subjectId, [...(porMateria.get(aula.subjectId) ?? []), aula]);
    }
    return porMateria;
  });
}
