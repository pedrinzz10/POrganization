import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { IsoDate, Occurrence } from '../data/commitment.model';
import { DateRange, daysOf, parseIsoDate, today } from '../data/date-range.util';
import { compareOccurrences, occurrenceKey } from './occurrence-order';

/** Sete colunas, de segunda a domingo, com as ocorrências de cada dia. */
@Component({
  selector: 'app-week-view',
  imports: [DatePipe],
  template: `
    <div class="week">
      @for (day of days(); track day.date) {
        <section class="week__day" [class.week__day--today]="day.date === hoje" [attr.aria-label]="day.label">
          <h3 class="week__header">{{ day.jsDate | date: 'EEE dd/MM' }}</h3>
          @for (item of day.items; track key(item)) {
            <button type="button" class="week__item" [class.done]="item.done" (click)="opened.emit(item)">
              <span class="week__time">{{ item.allDay ? 'Dia todo' : item.startTime }}</span>
              {{ item.title }}
            </button>
          } @empty {
            <span class="week__empty">—</span>
          }
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

  /** Agrupa por dia: recalculado sozinho quando o intervalo ou a lista mudam. */
  protected readonly days = computed(() => {
    const byDay = new Map<IsoDate, Occurrence[]>();
    for (const o of [...this.occurrences()].sort(compareOccurrences)) {
      byDay.set(o.occurrenceDate, [...(byDay.get(o.occurrenceDate) ?? []), o]);
    }
    return daysOf(this.range()).map((date) => {
      const jsDate = parseIsoDate(date);
      return { date, jsDate, label: jsDate.toLocaleDateString('pt-BR'), items: byDay.get(date) ?? [] };
    });
  });
}
