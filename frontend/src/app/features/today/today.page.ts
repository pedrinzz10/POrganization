import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../core/http/problem';
import { parseIsoDate } from '../commitments/data/date-range.util';
import { TodayService } from './data/today.service';
import { BrlPipe } from '../../shared/money-input/brl.pipe';
import { TodayAgendaComponent } from './sections/today-agenda.component';
import { TodayCommitmentsComponent } from './sections/today-commitments.component';
import { TodayFinanceComponent } from './sections/today-finance.component';
import { TodayStudiesComponent } from './sections/today-studies.component';
import { TodayTasksComponent } from './sections/today-tasks.component';

/**
 * Tela Hoje: busca tudo numa chamada (GET /api/today) e distribui para as seções
 * (compromissos, tarefas do dia, estudos e finanças). Cada seção cuida só da sua parte. No layout do
 * Intelly: cards de métrica no topo, seções em cards e a agenda do dia na coluna da direita.
 */
@Component({
  selector: 'app-today-page',
  imports: [
    DatePipe,
    MatProgressBarModule,
    MatButtonModule,
    BrlPipe,
    TodayAgendaComponent,
    TodayCommitmentsComponent,
    TodayFinanceComponent,
    TodayStudiesComponent,
    TodayTasksComponent,
  ],
  template: `
    <h1 class="titulo caption">Hoje</h1>
    <p class="saudacao display">{{ saudacao }}</p>
    @if (hoje(); as data) {
      <p class="data">{{ data | date: "EEEE, d 'de' MMMM" }}</p>
    }

    @if (tela.isLoading() && !tela.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
      <button mat-stroked-button type="button" (click)="tela.reload()">Tentar de novo</button>
    }
    @if (tela.hasValue()) {
      @let t = tela.value();
      <section class="metricas" aria-label="Resumo do dia">
        <div class="ds-card ds-card--lavender metrica">
          <h3>Compromissos</h3>
          <span class="ds-stat">{{ pendentes() }}</span>
          <span class="ds-unit">para hoje</span>
          <span class="ds-shape ds-shape--burst" aria-hidden="true"></span>
        </div>
        <div class="ds-card ds-card--steel metrica">
          <h3>Revisões</h3>
          <span class="ds-stat">{{ t.studies?.reviews?.length ?? 0 }}</span>
          <span class="ds-unit">de estudo</span>
          <span class="ds-shape ds-shape--tri" aria-hidden="true"></span>
        </div>
        @if (t.finance; as f) {
          <div class="ds-card metrica">
            <h3>Gasto hoje</h3>
            <span class="ds-stat">{{ f.spentToday | brl }}</span>
          </div>
          @if (f.balances; as b) {
            <div class="ds-card ds-card--ink metrica">
              <h3>Previsto no fim do mês</h3>
              <span class="ds-stat">{{ b.forecast | brl }}</span>
              <span class="ds-shape ds-shape--disc" aria-hidden="true"></span>
            </div>
          }
        }
      </section>

      <div class="layout">
        <div class="secoes">
          <app-today-commitments
            [occurrences]="tela.value().commitments"
            (changed)="tela.reload()"
          />
          <app-today-tasks [today]="tela.value().date" />
          @if (tela.value().studies; as estudos) {
            <app-today-studies [today]="tela.value().date" [plan]="estudos" />
          }
          @if (tela.value().finance; as financas) {
            <app-today-finance
              [today]="tela.value().date"
              [finance]="financas"
              (changed)="tela.reload()"
            />
          }
        </div>
        <aside class="lateral" aria-label="Agenda do dia">
          <app-today-agenda [today]="t.date" [occurrences]="t.commitments" />
        </aside>
      </div>
    }
  `,
  styles: `
    .titulo {
      margin: var(--space-2) 0 0;
      color: var(--fg-secondary);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .saudacao {
      margin: var(--space-1) 0;
    }
    .data {
      margin: 0 0 var(--space-6);
      font: 500 16px/22px var(--font-sans);
      color: var(--fg-secondary);
    }
    .data::first-letter {
      text-transform: uppercase;
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .metricas {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--space-4);
      margin-bottom: var(--space-4);
    }
    .metrica {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      min-height: 112px;
    }
    .metrica > :not(.ds-shape) {
      position: relative;
      z-index: 1;
    }
    .metrica .ds-shape {
      width: 76px;
      height: 76px;
      right: -26px;
      bottom: -30px;
    }
    .metrica .ds-stat {
      white-space: nowrap;
    }
    // No celular, dois cards por linha para o conteúdo aparecer logo
    @media (max-width: 599px) {
      .metricas {
        grid-template-columns: 1fr 1fr;
        gap: var(--space-3);
      }
      .metrica {
        min-height: 96px;
        padding: var(--space-4);
      }
      .metrica h3 {
        font-size: 13px;
        line-height: 18px;
      }
      .metrica .ds-stat {
        font-size: 20px;
        line-height: 26px;
      }
      .metrica .ds-shape {
        width: 52px;
        height: 52px;
        right: -20px;
        bottom: -24px;
      }
    }
    // Duas colunas no computador (seções + agenda do dia), uma no celular
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 320px;
      gap: var(--space-4);
      align-items: start;
    }
    @media (max-width: 1099px) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
    .secoes {
      display: grid;
      gap: var(--space-4);
      min-width: 0;
    }
    // Cada seção num card lavender-100; dentro dele, linhas e campos ficam em lavender-50
    .secoes > * {
      display: block;
      padding: var(--space-5);
      border-radius: var(--radius-lg);
      background: var(--bg-card);
      --mat-sys-surface-container-low: var(--bg-page);
      --mat-sys-surface-container-highest: var(--bg-page);
    }
  `,
})
export class TodayPage {
  private readonly today = inject(TodayService);

  protected readonly tela = rxResource({ stream: () => this.today.load() });

  /** Saudação pelo horário do aparelho, como no cabeçalho do Intelly. */
  protected readonly saudacao = saudacaoPara(new Date().getHours());

  /** Compromissos de hoje ainda não feitos. */
  protected readonly pendentes = computed(() =>
    this.tela.hasValue() ? this.tela.value().commitments.filter((o) => !o.done).length : 0,
  );

  /** A data vem da API (fuso do usuário), não do relógio do navegador. */
  protected readonly hoje = computed(() =>
    this.tela.hasValue() ? parseIsoDate(this.tela.value().date) : null,
  );

  protected readonly erro = computed(() =>
    this.tela.error()
      ? problemMessage(this.tela.error(), 'Não foi possível carregar o seu dia.')
      : null,
  );
}

/** "Bom dia" até 11h59, "Boa tarde" até 17h59, "Boa noite" no resto. */
export function saudacaoPara(hora: number): string {
  if (hora >= 5 && hora < 12) {
    return 'Bom dia';
  }
  return hora >= 12 && hora < 18 ? 'Boa tarde' : 'Boa noite';
}
