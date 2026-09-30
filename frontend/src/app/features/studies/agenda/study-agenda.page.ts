import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/http/problem';
import { IsoDate } from '../../commitments/data/commitment.model';
import {
  addDays,
  DateRange,
  monthGridRange,
  parseIsoDate,
  shiftMonth,
  today,
  weekRange,
  yearMonthOf,
} from '../../commitments/data/date-range.util';
import { StudiesService } from '../data/studies.service';
import { StudyCalendarDay, StudyCalendarItem } from '../data/study.model';

type Visao = 'dia' | 'semana' | 'mes';

const VISOES: { id: Visao; rotulo: string }[] = [
  { id: 'dia', rotulo: 'Dia' },
  { id: 'semana', rotulo: 'Semana' },
  { id: 'mes', rotulo: 'Mês' },
];

/**
 * Agenda de estudos, no mesmo formato da agenda de compromissos (Dia, Semana, Mês): até hoje o
 * que foi estudado; de hoje em diante as revisões agendadas e as aulas da meta semanal espalhadas
 * pelos dias (a previsão muda conforme as sessões acontecem).
 */
@Component({
  selector: 'app-study-agenda-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="topo">
      <div class="ds-seg" role="tablist" aria-label="Visualização">
        @for (v of visoes; track v.id) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="visao() === v.id"
            (click)="visao.set(v.id)"
          >
            {{ v.rotulo }}
          </button>
        }
      </div>
      <div class="nav">
        <button
          mat-icon-button
          type="button"
          [attr.aria-label]="rotuloAnterior()"
          (click)="mover(-1)"
        >
          <mat-icon aria-hidden="true">chevron_left</mat-icon>
        </button>
        <h2 class="nav__titulo">{{ titulo() }}</h2>
        <button
          mat-icon-button
          type="button"
          [attr.aria-label]="rotuloProximo()"
          (click)="mover(1)"
        >
          <mat-icon aria-hidden="true">chevron_right</mat-icon>
        </button>
        <button mat-button type="button" (click)="data.set(hoje)">Hoje</button>
      </div>
    </div>

    <ul class="legenda" aria-label="Legenda">
      <li><span class="amostra amostra--aula"></span>Aula sugerida</li>
      <li><span class="amostra amostra--revisao"></span>Revisão</li>
      <li><span class="amostra amostra--atrasada"></span>Revisão atrasada</li>
      <li><span class="amostra amostra--feito"></span>Estudado</li>
    </ul>

    @if (agenda.isLoading() && !agenda.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    @if (agenda.hasValue()) {
      @switch (visao()) {
        @case ('dia') {
          @let d = agenda.value()[0];
          <section class="dia" [attr.aria-label]="d.date + 'T12:00' | date: 'fullDate'">
            @if (d.items.length > 0) {
              <p class="resumo">
                {{ totalMinutos(d) }} min · {{ d.items.length }}
                {{ d.items.length === 1 ? 'item' : 'itens' }}
              </p>
            }
            <ul class="lista">
              @for (item of d.items; track $index) {
                <li class="card" [class]="classe(item)">
                  <span
                    class="card__bolinha"
                    [style.background]="item.color ?? null"
                    aria-hidden="true"
                  ></span>
                  <span class="card__texto">
                    <span class="card__titulo">{{ titulo_(item) }}</span>
                    <span class="card__detalhe">{{ detalhe(item) }}</span>
                  </span>
                  <span class="card__min">{{ item.minutes }} min</span>
                </li>
              } @empty {
                <li class="vazio">
                  {{ d.date < hoje ? 'Nada estudado neste dia.' : 'Nada para estudar neste dia.' }}
                </li>
              }
            </ul>
          </section>
        }
        @case ('semana') {
          <div class="semana">
            @for (d of agenda.value(); track d.date) {
              <section
                class="semana__dia"
                [class.semana__dia--hoje]="d.date === hoje"
                [attr.aria-label]="d.date + 'T12:00' | date: 'fullDate'"
              >
                <button type="button" class="semana__cabecalho" (click)="abrirDia(d.date)">
                  <span class="semana__dow">{{ d.date + 'T12:00' | date: 'EEE' }}</span>
                  <span class="semana__data">{{ d.date + 'T12:00' | date: 'dd/MM' }}</span>
                </button>
                <div class="semana__itens">
                  @for (item of d.items; track $index) {
                    <div class="card card--compacto" [class]="classe(item)">
                      <span class="card__titulo">{{ titulo_(item) }}</span>
                      <span class="card__detalhe">{{ detalheCurto(item) }}</span>
                    </div>
                  } @empty {
                    <span class="semana__vazio">—</span>
                  }
                </div>
              </section>
            }
          </div>
        }
        @case ('mes') {
          <div class="mes" role="group" [attr.aria-label]="titulo()">
            @for (s of semanaCurta; track $index) {
              <span class="mes__dow" aria-hidden="true">{{ s }}</span>
            }
            @for (d of agenda.value(); track d.date) {
              <button
                type="button"
                class="mes__dia"
                [class.mes__dia--fora]="!noMes(d.date)"
                [class.mes__dia--hoje]="d.date === hoje"
                [attr.aria-label]="rotuloDiaMes(d)"
                (click)="abrirDia(d.date)"
              >
                <span class="mes__numero">{{ d.date.slice(8, 10) }}</span>
                @for (item of d.items.slice(0, 3); track $index) {
                  <span class="mes__item" [class]="classe(item)">{{ item.subjectName }}</span>
                }
                @if (d.items.length > 3) {
                  <span class="mes__mais">+{{ d.items.length - 3 }}</span>
                }
              </button>
            }
          </div>
        }
      }
    }
  `,
  styleUrl: './study-agenda.page.scss',
})
export class StudyAgendaPage {
  private readonly studies = inject(StudiesService);

  protected readonly visoes = VISOES;
  protected readonly semanaCurta = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'];
  protected readonly hoje: IsoDate = today();

  readonly visao = signal<Visao>('semana');
  readonly data = signal<IsoDate>(this.hoje);

  protected readonly intervalo = computed<DateRange>(() => {
    const d = this.data();
    switch (this.visao()) {
      case 'dia':
        return { from: d, to: d };
      case 'semana':
        return weekRange(d);
      case 'mes': {
        const { year, month } = yearMonthOf(d);
        return monthGridRange(year, month);
      }
    }
  });

  protected readonly agenda = rxResource({
    params: () => this.intervalo(),
    stream: ({ params }) => this.studies.calendar(params.from, params.to),
  });

  protected readonly erro = computed(() =>
    this.agenda.error()
      ? problemMessage(this.agenda.error(), 'Não foi possível carregar a agenda de estudos.')
      : null,
  );

  protected readonly titulo = computed(() => {
    const r = this.intervalo();
    const fmt = (iso: IsoDate, opts: Intl.DateTimeFormatOptions) =>
      parseIsoDate(iso).toLocaleDateString('pt-BR', opts);
    switch (this.visao()) {
      case 'dia':
        return capitalizar(fmt(r.from, { weekday: 'long', day: 'numeric', month: 'long' }));
      case 'semana':
        return `${fmt(r.from, { day: '2-digit', month: '2-digit' })} – ${fmt(r.to, { day: '2-digit', month: '2-digit', year: 'numeric' })}`;
      case 'mes':
        return capitalizar(fmt(this.data().slice(0, 8) + '01', { month: 'long', year: 'numeric' }));
    }
  });

  protected readonly rotuloAnterior = computed(
    () => ({ dia: 'Dia anterior', semana: 'Semana anterior', mes: 'Mês anterior' })[this.visao()],
  );
  protected readonly rotuloProximo = computed(
    () => ({ dia: 'Próximo dia', semana: 'Próxima semana', mes: 'Próximo mês' })[this.visao()],
  );

  mover(delta: number): void {
    const d = this.data();
    switch (this.visao()) {
      case 'dia':
        this.data.set(addDays(d, delta));
        break;
      case 'semana':
        this.data.set(addDays(d, 7 * delta));
        break;
      case 'mes': {
        const { year, month } = shiftMonth(yearMonthOf(d), delta);
        this.data.set(`${year}-${String(month).padStart(2, '0')}-01`);
        break;
      }
    }
  }

  abrirDia(date: IsoDate): void {
    this.data.set(date);
    this.visao.set('dia');
  }

  protected noMes(date: IsoDate): boolean {
    return date.slice(0, 7) === this.data().slice(0, 7);
  }

  protected classe(item: StudyCalendarItem): string {
    if (item.kind === 'DONE') {
      return 'item--feito';
    }
    if (item.kind === 'LESSON') {
      return 'item--aula';
    }
    return item.overdue ? 'item--atrasada' : 'item--revisao';
  }

  /** "Aula de Java", "Revisão: Streams" ou "✓ Java: Streams". */
  protected titulo_(item: StudyCalendarItem): string {
    switch (item.kind) {
      case 'LESSON':
        return `Aula de ${item.subjectName}`;
      case 'REVIEW':
        return `Revisão: ${item.title}`;
      case 'DONE':
        return `✓ ${item.subjectName}${item.title ? ': ' + item.title : ''}`;
    }
  }

  protected detalhe(item: StudyCalendarItem): string {
    switch (item.kind) {
      case 'LESSON':
        return 'Sugerida para a meta da semana';
      case 'REVIEW':
        return `${item.subjectName}${item.overdue ? ' · atrasada' : ''}`;
      case 'DONE':
        return item.sessionType === 'REVIEW' ? 'Revisão feita' : 'Aula feita';
    }
  }

  protected detalheCurto(item: StudyCalendarItem): string {
    const base = item.kind === 'REVIEW' ? item.subjectName : `${item.minutes} min`;
    return item.overdue ? `${base} · atrasada` : base;
  }

  protected totalMinutos(d: StudyCalendarDay): number {
    return d.items.reduce((soma, i) => soma + i.minutes, 0);
  }

  protected rotuloDiaMes(d: StudyCalendarDay): string {
    const data = parseIsoDate(d.date).toLocaleDateString('pt-BR', {
      day: 'numeric',
      month: 'long',
    });
    return d.items.length === 0
      ? `${data}: nada`
      : `${data}: ${d.items.length} ${d.items.length === 1 ? 'item' : 'itens'}`;
  }
}

function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
