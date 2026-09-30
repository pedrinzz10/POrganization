import { Component, computed, input, output } from '@angular/core';
import { IsoDate, Occurrence } from '../data/commitment.model';
import { monthGrid, today } from '../data/date-range.util';
import { compareOccurrences, occurrenceKey } from './occurrence-order';

const MAX_TITULOS = 3;
const DIAS_DA_SEMANA = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];

/** Grade 6x7 do mês: até 3 títulos por dia e "+N" para o resto. Clicar num dia avisa o pai. */
@Component({
  selector: 'app-month-view',
  template: `
    <div class="month" role="grid">
      @for (nome of diasDaSemana; track nome) {
        <div class="month__weekday" role="columnheader">{{ nome }}</div>
      }
      @for (cell of cells(); track cell.date) {
        <button
          type="button"
          class="cell"
          role="gridcell"
          [attr.data-date]="cell.date"
          [class.fora-do-mes]="!cell.inMonth"
          [class.hoje]="cell.date === hoje"
          [attr.aria-label]="cell.label"
          (click)="daySelected.emit(cell.date)"
        >
          <span class="cell__day">{{ cell.day }}</span>
          @for (item of cell.visible; track key(item)) {
            <span class="cell__item" [class.done]="item.done">{{ item.title }}</span>
          }
          @if (cell.hidden > 0) {
            <span class="cell__more">+{{ cell.hidden }}</span>
          }
        </button>
      }
    </div>
  `,
  styleUrl: './month-view.component.scss',
})
export class MonthViewComponent {
  readonly year = input.required<number>();
  /** 1 a 12 */
  readonly month = input.required<number>();
  readonly occurrences = input.required<Occurrence[]>();

  readonly daySelected = output<IsoDate>();

  protected readonly diasDaSemana = DIAS_DA_SEMANA;
  protected readonly hoje = today();
  protected readonly key = occurrenceKey;

  protected readonly cells = computed(() => {
    const byDay = new Map<IsoDate, Occurrence[]>();
    for (const o of [...this.occurrences()].sort(compareOccurrences)) {
      byDay.set(o.occurrenceDate, [...(byDay.get(o.occurrenceDate) ?? []), o]);
    }
    const prefix = `${this.year()}-${String(this.month()).padStart(2, '0')}-`;
    return monthGrid(this.year(), this.month()).map((date) => {
      const items = byDay.get(date) ?? [];
      const count = items.length;
      return {
        date,
        day: Number(date.slice(8)),
        inMonth: date.startsWith(prefix),
        visible: items.slice(0, MAX_TITULOS),
        hidden: Math.max(0, count - MAX_TITULOS),
        label: `${date.split('-').reverse().join('/')}: ${count} compromisso${count === 1 ? '' : 's'}`,
      };
    });
  });
}
