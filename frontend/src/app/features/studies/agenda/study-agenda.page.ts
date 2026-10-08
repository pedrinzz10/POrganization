import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom, Observable } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../../shared/confirm-dialog/confirm.dialog';
import { IsoDate } from '../../commitments/data/commitment.model';
import {
  addDays,
  daysOf,
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

/** O que viaja no arrasto: a aula e o dia de onde ela saiu. */
interface Arrasto {
  item: StudyCalendarItem;
  from: IsoDate;
}

const VISOES: { id: Visao; rotulo: string }[] = [
  { id: 'dia', rotulo: 'Dia' },
  { id: 'semana', rotulo: 'Semana' },
  { id: 'mes', rotulo: 'Mês' },
];

/**
 * Agenda de estudos, no mesmo formato da agenda de compromissos (Dia, Semana, Mês): até hoje o
 * que foi estudado; de hoje em diante as revisões agendadas e as aulas da meta semanal espalhadas
 * pelos dias (a previsão muda conforme as sessões acontecem). Na matéria com aulas definidas, a
 * aula mostra qual é (a próxima da lista).
 *
 * Plano da semana (E15): "Gerar semana" sorteia os dias; na Semana dá para arrastar a aula para
 * outro dia, incluir (+ Aula) e tirar (×). Mexer numa semana automática transforma a previsão em
 * plano; "Voltar ao automático" desfaz. No Dia, o menu da aula faz o mesmo sem mouse.
 */
@Component({
  selector: 'app-study-agenda-page',
  imports: [
    CdkDrag,
    CdkDropList,
    CdkDropListGroup,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
  ],
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
      <li><span class="amostra amostra--aula"></span>Aula</li>
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
                  @if (item.pinned) {
                    <mat-icon class="card__pino" aria-label="Fixada neste dia">push_pin</mat-icon>
                  }
                  <span class="ds-chip">{{ item.minutes }} min</span>
                  @if (movivel(item, d.date)) {
                    <button
                      mat-icon-button
                      type="button"
                      class="card__acoes"
                      [matMenuTriggerFor]="acoes"
                      [attr.aria-label]="'Opções da aula de ' + item.subjectName"
                    >
                      <mat-icon aria-hidden="true">more_vert</mat-icon>
                    </button>
                    <mat-menu #acoes="matMenu">
                      @for (destino of destinos(d.date); track destino) {
                        <button mat-menu-item type="button" (click)="moverAula(item, d.date, destino)">
                          Mover para {{ destino + 'T12:00' | date: "EEEE, dd/MM" }}
                        </button>
                      }
                      <button mat-menu-item type="button" (click)="tirarAula(item, d.date)">
                        Tirar deste dia
                      </button>
                    </mat-menu>
                  }
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
          <div class="plano">
            @if (planejada()) {
              <span class="ds-chip plano__selo">
                <mat-icon aria-hidden="true">event_note</mat-icon>
                Plano da semana
              </span>
              <span class="plano__dica">Arraste para outro dia, inclua (+ Aula) ou tire (×).</span>
            } @else {
              <span class="plano__dica">
                Previsão automática pela meta de cada matéria. Gere a semana ou ajuste para montar seu plano.
              </span>
            }
            @if (editavel()) {
              <span class="plano__acoes">
                @if (planejada()) {
                  <button mat-button type="button" [disabled]="ocupado()" (click)="limparPlano()">
                    Voltar ao automático
                  </button>
                }
                <button mat-flat-button type="button" [disabled]="ocupado()" (click)="gerarSemana()">
                  <mat-icon aria-hidden="true">shuffle</mat-icon>
                  Gerar semana
                </button>
              </span>
            }
          </div>
          <mat-menu #incluirMenu="matMenu">
            <ng-template matMenuContent let-date="date">
              @for (m of materias(); track m.id) {
                <button mat-menu-item type="button" (click)="incluirAula(m.id, m.name, date)">
                  <span class="menu__cor" [style.background]="m.color ?? null" aria-hidden="true"></span>
                  {{ m.name }}
                </button>
              } @empty {
                <span class="menu__vazio">{{ materiasCarregando() ? 'Carregando…' : 'Nenhuma matéria.' }}</span>
              }
            </ng-template>
          </mat-menu>
          <div class="semana" cdkDropListGroup>
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
                <div
                  class="semana__itens"
                  cdkDropList
                  [cdkDropListData]="d.date"
                  [cdkDropListEnterPredicate]="podeSoltar"
                  [cdkDropListSortingDisabled]="true"
                  (cdkDropListDropped)="soltar($event)"
                >
                  @for (item of d.items; track $index) {
                    <div
                      class="card card--compacto"
                      [class]="classe(item)"
                      [class.card--arrastavel]="movivel(item, d.date)"
                      cdkDrag
                      [cdkDragData]="{ item, from: d.date }"
                      [cdkDragDisabled]="!movivel(item, d.date)"
                    >
                      <span class="card__titulo">
                        @if (item.pinned) {
                          <mat-icon class="card__pino" aria-label="Fixada neste dia">push_pin</mat-icon>
                        }
                        {{ titulo_(item) }}
                      </span>
                      <span class="card__detalhe">{{ detalheCurto(item) }}</span>
                      @if (movivel(item, d.date)) {
                        <button
                          type="button"
                          class="card__tirar"
                          [attr.aria-label]="'Tirar a aula de ' + item.subjectName + ' de ' + (d.date + 'T12:00' | date: 'dd/MM')"
                          (click)="tirarAula(item, d.date)"
                        >
                          <mat-icon aria-hidden="true">close</mat-icon>
                        </button>
                      }
                    </div>
                  } @empty {
                    <span class="semana__vazio">—</span>
                  }
                  @if (d.date >= hoje) {
                    <button
                      type="button"
                      class="semana__incluir"
                      [matMenuTriggerFor]="incluirMenu"
                      [matMenuTriggerData]="{ date: d.date }"
                      [attr.aria-label]="'Incluir aula em ' + (d.date + 'T12:00' | date: 'dd/MM')"
                      (menuOpened)="querMaterias.set(true)"
                    >
                      + Aula
                    </button>
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
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

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

  /** A semana na tela tem plano montado pelo usuário. */
  protected readonly planejada = computed(
    () => this.agenda.hasValue() && this.agenda.value().some((d) => d.planned),
  );
  /** Semana com algum dia de hoje em diante: dá para gerar e ajustar. */
  protected readonly editavel = computed(() => this.intervalo().to >= this.hoje);
  protected readonly ocupado = signal(false);

  /** Matérias para o "+ Aula": só busca quando o menu abre pela primeira vez. */
  protected readonly querMaterias = signal(false);
  private readonly materiasRes = rxResource({
    params: () => (this.querMaterias() ? true : undefined),
    stream: () => this.studies.listSubjects(),
  });
  protected readonly materias = computed(() =>
    this.materiasRes.hasValue() ? this.materiasRes.value() : [],
  );
  protected readonly materiasCarregando = computed(() => this.materiasRes.isLoading());

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

  /** Só a aula sugerida, de hoje em diante, muda de dia. */
  protected movivel(item: StudyCalendarItem, date: IsoDate): boolean {
    return item.kind === 'LESSON' && date >= this.hoje;
  }

  /** Dias da mesma semana, de hoje em diante, para onde a aula pode ir. */
  protected destinos(date: IsoDate): IsoDate[] {
    return daysOf(weekRange(date)).filter((d) => d >= this.hoje && d !== date);
  }

  /** Solta só em dia de hoje em diante e na mesma semana da origem (a meta é semanal). */
  protected readonly podeSoltar = (drag: CdkDrag<Arrasto>, drop: CdkDropList<IsoDate>): boolean =>
    drop.data >= this.hoje && weekRange(drop.data).from === weekRange(drag.data.from).from;

  protected soltar(event: CdkDragDrop<IsoDate, IsoDate, Arrasto>): void {
    if (event.previousContainer === event.container) {
      return;
    }
    void this.moverAula(event.item.data.item, event.item.data.from, event.container.data);
  }

  /** Move na tela na hora; se a API recusar, recarrega e avisa. */
  async moverAula(item: StudyCalendarItem, from: IsoDate, to: IsoDate): Promise<void> {
    this.agenda.update((dias) =>
      dias?.map((d) => {
        if (d.date === from) {
          return { ...d, items: d.items.filter((i) => i !== item) };
        }
        if (d.date === to) {
          return { ...d, items: [...d.items, { ...item, pinned: true }] };
        }
        return d;
      }),
    );
    try {
      await firstValueFrom(this.studies.moveLesson(item.subjectId, from, to));
      this.snackBar.open(`Aula de ${item.subjectName} mudou de dia.`, 'OK', { duration: 3000 });
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível mover a aula.'), 'OK', { duration: 5000 });
    }
    this.agenda.reload();
  }

  /** "Gerar semana": sorteia de novo; numa semana já planejada, confirma antes de trocar. */
  async gerarSemana(): Promise<void> {
    if (this.planejada()) {
      const confirmou = await firstValueFrom(
        this.dialog
          .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
            data: {
              title: 'Gerar a semana de novo?',
              message: 'As aulas de hoje até domingo são sorteadas de novo; os ajustes que você fez saem.',
              confirmLabel: 'Gerar',
            },
          })
          .afterClosed(),
      );
      if (!confirmou) {
        return;
      }
    }
    await this.acaoNoPlano(
      () => this.studies.generateWeek(this.intervalo().from),
      'Semana gerada.',
      'Não foi possível gerar a semana.',
    );
  }

  async limparPlano(): Promise<void> {
    await this.acaoNoPlano(
      () => this.studies.clearWeek(this.intervalo().from),
      'A semana voltou para a previsão automática.',
      'Não foi possível voltar ao automático.',
    );
  }

  async incluirAula(subjectId: string, nome: string, date: IsoDate): Promise<void> {
    await this.acaoNoPlano(
      () => this.studies.addWeekLesson(subjectId, date),
      `Aula de ${nome} incluída.`,
      'Não foi possível incluir a aula.',
    );
  }

  async tirarAula(item: StudyCalendarItem, date: IsoDate): Promise<void> {
    await this.acaoNoPlano(
      () => this.studies.removeWeekLesson(item.subjectId, date),
      `Aula de ${item.subjectName} tirada do dia.`,
      'Não foi possível tirar a aula.',
    );
  }

  /** Chama a API, avisa e recarrega o que está na tela (a resposta é só a semana). */
  private async acaoNoPlano(
    acao: () => Observable<unknown>,
    ok: string,
    falha: string,
  ): Promise<void> {
    this.ocupado.set(true);
    try {
      await firstValueFrom(acao());
      this.snackBar.open(ok, 'OK', { duration: 3000 });
    } catch (error) {
      this.snackBar.open(problemMessage(error, falha), 'OK', { duration: 5000 });
    } finally {
      this.ocupado.set(false);
    }
    this.agenda.reload();
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
        return item.title ? `${item.subjectName}: ${item.title}` : `Aula de ${item.subjectName}`;
      case 'REVIEW':
        return `Revisão: ${item.title}`;
      case 'DONE':
        return `✓ ${item.subjectName}${item.title ? ': ' + item.title : ''}`;
    }
  }

  protected detalhe(item: StudyCalendarItem): string {
    switch (item.kind) {
      case 'LESSON':
        return item.title ? 'Próxima aula da lista' : 'Aula da meta da semana';
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
