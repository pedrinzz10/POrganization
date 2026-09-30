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
  imports: [DatePipe, MatProgressBarModule, MatButtonModule, TodayCommitmentsComponent, TodayFinanceComponent, TodayStudiesComponent, TodayTasksComponent],
  template: `
    <h1 class="titulo">Hoje</h1>
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
          <app-today-finance [today]="tela.value().date" [finance]="financas" (changed)="tela.reload()" />
        }
      </div>
    }
  `,
  styles: `
    .titulo {
      font: var(--mat-sys-headline-medium);
      margin: 8px 0 0;
    }
    .data {
      margin: 0 0 16px;
      color: var(--mat-sys-on-surface-variant);
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

  /** A data vem da API (fuso do usuário), não do relógio do navegador. */
  protected readonly hoje = computed(() => (this.tela.hasValue() ? parseIsoDate(this.tela.value().date) : null));

  protected readonly erro = computed(() =>
    this.tela.error() ? problemMessage(this.tela.error(), 'Não foi possível carregar o seu dia.') : null,
  );
}
