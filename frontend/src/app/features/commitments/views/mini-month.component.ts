import { Component, computed, input, output } from '@angular/core';
import { IsoDate } from '../data/commitment.model';
import { monthGrid, parseIsoDate, today } from '../data/date-range.util';

/**
 * Mês em miniatura, reutilizado 12 vezes na visão do ano: recebe o mês por input() e marca
 * os dias que têm compromisso.
 */
@Component({
  selector: 'app-mini-month',
  template: `
    <button type="button" class="mini__title" (click)="monthSelected.emit()">{{ title() }}</button>
    <div class="mini__grid">
      @for (cell of cells(); track cell.date) {
        <span
          class="mini__day"
          [attr.data-date]="cell.inMonth ? cell.date : null"
          [class.fora]="!cell.inMonth"
          [class.ocupado]="cell.inMonth && cell.busy"
          [class.hoje]="cell.inMonth && cell.date === hoje"
          >{{ cell.inMonth ? cell.day : '' }}</span
        >
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .mini__title {
      border: none;
      background: none;
      padding: 0 0 4px;
      font: var(--mat-sys-title-small);
      color: inherit;
      cursor: pointer;
    }
    .mini__title::first-letter {
      text-transform: uppercase;
    }
    .mini__grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 1px;
      text-align: center;
      font: var(--mat-sys-body-small);
    }
    .mini__day {
      aspect-ratio: 1;
      display: grid;
      place-items: center;
      border-radius: 50%;
    }
    .mini__day.ocupado {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font-weight: 600;
    }
    .mini__day.hoje {
      outline: 2px solid var(--mat-sys-primary);
    }
  `,
})
export class MiniMonthComponent {
  readonly year = input.required<number>();
  readonly month = input.required<number>();
  readonly busyDays = input.required<ReadonlySet<IsoDate>>();

  readonly monthSelected = output<void>();

  protected readonly hoje = today();

  protected readonly title = computed(() =>
    parseIsoDate(`${this.year()}-${String(this.month()).padStart(2, '0')}-01`).toLocaleDateString('pt-BR', {
      month: 'long',
    }),
  );

  protected readonly cells = computed(() => {
    const prefix = `${this.year()}-${String(this.month()).padStart(2, '0')}-`;
    const busy = this.busyDays();
    return monthGrid(this.year(), this.month()).map((date) => ({
      date,
      day: Number(date.slice(8)),
      inMonth: date.startsWith(prefix),
      busy: busy.has(date),
    }));
  });
}
