import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../core/http/problem';
import { parseIsoDate } from '../commitments/data/date-range.util';
import { TodayService } from './data/today.service';
import { TodayCommitmentsComponent } from './sections/today-commitments.component';
import { TodayFinanceComponent } from './sections/today-finance.component';
import { TodayStudiesComponent } from './sections/today-studies.component';
import { TodayTasksComponent } from './sections/today-tasks.component';

/**
 * Tela Hoje: busca tudo numa chamada (GET /api/today) e distribui para as seções
 * (compromissos, tarefas do dia, estudos e finanças). Cada seção cuida só da sua parte.
 */
@Component({
  selector: 'app-today-page',
  imports: [
    DatePipe,
    MatProgressBarModule,
    MatButtonModule,
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
      <div class="secoes">
        <app-today-commitments [occurrences]="tela.value().commitments" (changed)="tela.reload()" />
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
    .secoes {
      display: grid;
      gap: 24px;
    }
  `,
})
export class TodayPage {
  private readonly today = inject(TodayService);

  protected readonly tela = rxResource({ stream: () => this.today.load() });

  /** Saudação pelo horário do aparelho, como no cabeçalho do Intelly. */
  protected readonly saudacao = saudacaoPara(new Date().getHours());

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
