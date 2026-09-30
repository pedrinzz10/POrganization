import { Component, computed, inject, input, output } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/http/problem';
import { IsoDate } from '../data/commitment.model';
import { CommitmentsService } from '../data/commitments.service';
import { yearRange } from '../data/date-range.util';
import { MiniMonthComponent } from './mini-month.component';

/** Os 12 meses do ano em miniatura, marcando os dias ocupados. Uma única consulta pelo ano todo. */
@Component({
  selector: 'app-year-view',
  imports: [MiniMonthComponent, MatProgressBarModule],
  template: `
    @if (ocorrencias.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }
    <div class="year">
      @for (month of months; track month) {
        <app-mini-month
          [year]="year()"
          [month]="month"
          [busyDays]="ocupados()"
          (monthSelected)="monthSelected.emit(month)"
        />
      }
    </div>
  `,
  styles: `
    .year {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 24px;
      padding-top: 8px;
    }
    .erro {
      color: var(--mat-sys-error);
    }
  `,
})
export class YearViewComponent {
  private readonly commitments = inject(CommitmentsService);

  readonly year = input.required<number>();
  /** Mês (1 a 12) escolhido, para o pai abrir a visão do mês. */
  readonly monthSelected = output<number>();

  protected readonly months = Array.from({ length: 12 }, (_, i) => i + 1);

  protected readonly ocorrencias = rxResource({
    params: () => yearRange(this.year()),
    stream: ({ params }) => this.commitments.findInRange(params.from, params.to),
  });

  protected readonly ocupados = computed<ReadonlySet<IsoDate>>(
    () => new Set((this.ocorrencias.hasValue() ? this.ocorrencias.value() : []).map((o) => o.occurrenceDate)),
  );

  protected readonly erro = computed(() =>
    this.ocorrencias.error() ? problemMessage(this.ocorrencias.error(), 'Não foi possível carregar o ano.') : null,
  );
}
