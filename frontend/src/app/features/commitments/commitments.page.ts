import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { problemMessage } from '../../core/http/problem';
import { CommitmentsService } from './data/commitments.service';
import { DateRange, nextWeek, parseIsoDate, previousWeek, today, weekRange } from './data/date-range.util';
import { QuickAddComponent } from './quick-add/quick-add.component';
import { DayViewComponent } from './views/day-view.component';
import { WeekViewComponent } from './views/week-view.component';

type Aba = 'hoje' | 'semana' | 'mes' | 'ano';
const ABAS: Aba[] = ['hoje', 'semana', 'mes', 'ano'];

@Component({
  selector: 'app-commitments-page',
  imports: [
    DatePipe,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    QuickAddComponent,
    DayViewComponent,
    WeekViewComponent,
  ],
  templateUrl: './commitments.page.html',
  styleUrl: './commitments.page.scss',
})
export class CommitmentsPage {
  private readonly commitments = inject(CommitmentsService);

  protected readonly aba = signal<Aba>('hoje');
  protected readonly semana = signal<DateRange>(weekRange(today()));
  protected readonly hoje = today();
  protected readonly hojeData = parseIsoDate(this.hoje);

  /** Intervalo que a aba atual precisa; mudar aba ou semana recarrega sozinho. */
  private readonly intervalo = computed<DateRange | undefined>(() => {
    switch (this.aba()) {
      case 'hoje':
        return { from: this.hoje, to: this.hoje };
      case 'semana':
        return this.semana();
      default:
        return undefined; // Mês e Ano chegam na C08
    }
  });

  protected readonly ocorrencias = rxResource({
    params: () => this.intervalo(),
    stream: ({ params }) => this.commitments.findInRange(params.from, params.to),
  });

  protected readonly erro = computed(() =>
    this.ocorrencias.error() ? problemMessage(this.ocorrencias.error(), 'Não foi possível carregar os compromissos.') : null,
  );

  protected selecionarAba(index: number): void {
    this.aba.set(ABAS[index]);
  }

  protected semanaAnterior(): void {
    this.semana.update(previousWeek);
  }

  protected proximaSemana(): void {
    this.semana.update(nextWeek);
  }

  protected semanaAtual(): void {
    this.semana.set(weekRange(today()));
  }

  protected recarregar(): void {
    this.ocorrencias.reload();
  }
}
