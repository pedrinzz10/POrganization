import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IsoDate, Occurrence } from '../../commitments/data/commitment.model';
import { monthGrid, parseIsoDate, yearMonthOf } from '../../commitments/data/date-range.util';
import { compareOccurrences } from '../../commitments/views/occurrence-order';

const SEMANA = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'];

/**
 * Coluna da direita da tela Hoje, como no painel do Intelly: mini calendário do mês (hoje em
 * destaque, .ds-cal) e a linha do tempo dos compromissos do dia (.ds-listrow com o horário).
 */
@Component({
  selector: 'app-today-agenda',
  imports: [DatePipe, RouterLink],
  template: `
    <section class="bloco" aria-labelledby="hoje-calendario">
      <div class="ds-cal calendario">
        <div class="hd">
          <h2 id="hoje-calendario" class="mo">{{ dia() | date: "MMMM 'de' yyyy" }}</h2>
        </div>
        <div class="g" role="presentation">
          @for (s of semana; track $index) {
            <span class="w" aria-hidden="true">{{ s }}</span>
          }
          @for (c of celulas(); track c.date) {
            <span
              class="d"
              [class.o]="!c.noMes"
              [class.sel]="c.date === today()"
              [attr.aria-current]="c.date === today() ? 'date' : null"
              >{{ c.dia }}</span
            >
          }
        </div>
      </div>
    </section>

    <section class="bloco" aria-labelledby="hoje-linha-do-tempo">
      <div class="bloco__topo">
        <h2 id="hoje-linha-do-tempo" class="bloco__titulo">Linha do tempo</h2>
        <a class="bloco__link" routerLink="/compromissos">Ver agenda</a>
      </div>
      <ul class="ds-list linha">
        @for (o of ordenados(); track o.commitmentId + o.occurrenceDate + $index) {
          <li
            class="ds-listrow"
            [class.ds-listrow--active]="o === proximo()"
            [class.feito]="o.done"
          >
            <span class="n">{{ o.title }}</span>
            <span class="ds-time">{{ o.allDay || !o.startTime ? 'Dia todo' : o.startTime }}</span>
          </li>
        } @empty {
          <li class="vazio">Nada marcado para hoje.</li>
        }
      </ul>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    .bloco {
      padding: var(--space-5);
      border-radius: var(--radius-lg);
      background: var(--bg-card);
    }
    .calendario {
      width: 100%;
    }
    .mo {
      margin: 0;
    }
    .mo::first-letter {
      text-transform: uppercase;
    }
    .calendario .d.o {
      opacity: 0.55;
    }
    .bloco__topo {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: var(--space-3);
    }
    .bloco__titulo {
      margin: 0;
      font: var(--mat-sys-title-medium);
    }
    .bloco__link {
      font: 600 12px/16px var(--font-sans);
      color: var(--fg-primary);
    }
    .linha {
      max-width: none;
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .linha .ds-listrow {
      background: var(--bg-page);
    }
    .linha .ds-listrow--active {
      background: var(--surface-lavender);
    }
    .feito .n {
      text-decoration: line-through;
      color: var(--fg-secondary);
    }
    .vazio {
      font: var(--mat-sys-body-small);
      color: var(--fg-secondary);
    }
  `,
})
export class TodayAgendaComponent {
  /** Hoje no fuso do usuário (vem da API). */
  readonly today = input.required<IsoDate>();
  readonly occurrences = input.required<Occurrence[]>();

  protected readonly semana = SEMANA;
  protected readonly dia = computed(() => parseIsoDate(this.today()));

  protected readonly celulas = computed(() => {
    const { year, month } = yearMonthOf(this.today());
    const prefixo = `${year}-${String(month).padStart(2, '0')}`;
    return monthGrid(year, month).map((date) => ({
      date,
      dia: Number(date.slice(8, 10)),
      noMes: date.startsWith(prefixo),
    }));
  });

  protected readonly ordenados = computed(() => [...this.occurrences()].sort(compareOccurrences));

  /** O próximo compromisso ainda não feito fica em destaque (lavender). */
  protected readonly proximo = computed(
    () => this.ordenados().find((o) => !o.done && !o.allDay) ?? null,
  );
}
