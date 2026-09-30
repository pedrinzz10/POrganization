import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { IsoDate, Occurrence } from '../data/commitment.model';
import { DateRange, daysOf, parseIsoDate, today } from '../data/date-range.util';
import { compareOccurrences, occurrenceKey } from './occurrence-order';

/**
 * Sete colunas, de segunda a domingo, no estilo da agenda do Intelly: o dia em pílula (hoje em ink)
 * e cada ocorrência num card (lavender com horário, steel o dia todo, concluída apagada).
 */
@Component({
  selector: 'app-week-view',
  imports: [DatePipe],
  template: `
    <div class="week">
      @for (day of days(); track day.date) {
        <section
          class="week__day"
          [class.week__day--today]="day.date === hoje"
          [attr.aria-label]="day.label"
        >
          <h3 class="week__header">
            <span class="week__weekday">{{ day.jsDate | date: 'EEE' }}</span>
            <span class="week__date">{{ day.jsDate | date: 'dd/MM' }}</span>
          </h3>
          <div class="week__items">
            @for (item of day.items; track key(item)) {
              <button
                type="button"
                class="week__item"
                [class.week__item--dia-todo]="item.allDay"
                [class.done]="item.done"
                (click)="opened.emit(item)"
              >
                <span class="week__title">{{ item.title }}</span>
                <span class="week__time">{{ horario(item) }}</span>
                @if (item.location) {
                  <span class="week__local">{{ item.location }}</span>
                }
              </button>
            } @empty {
              <span class="week__empty">—</span>
            }
          </div>
        </section>
      }
    </div>
  `,
  styleUrl: './week-view.component.scss',
})
export class WeekViewComponent {
  readonly range = input.required<DateRange>();
  readonly occurrences = input.required<Occurrence[]>();
  readonly opened = output<Occurrence>();

  protected readonly hoje = today();
  protected readonly key = occurrenceKey;

  /** "Dia todo", "08:00" ou "08:00 – 09:30". */
  protected horario(item: Occurrence): string {
    if (item.allDay || !item.startTime) {
      return 'Dia todo';
    }
    return item.endTime ? `${item.startTime} – ${item.endTime}` : item.startTime;
  }

  /** Agrupa por dia: recalculado sozinho quando o intervalo ou a lista mudam. */
  protected readonly days = computed(() => {
    const byDay = new Map<IsoDate, Occurrence[]>();
    for (const o of [...this.occurrences()].sort(compareOccurrences)) {
      byDay.set(o.occurrenceDate, [...(byDay.get(o.occurrenceDate) ?? []), o]);
    }
    return daysOf(this.range()).map((date) => {
      const jsDate = parseIsoDate(date);
      return {
        date,
        jsDate,
        label: jsDate.toLocaleDateString('pt-BR'),
        items: byDay.get(date) ?? [],
      };
    });
  });
}
