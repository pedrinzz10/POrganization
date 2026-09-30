import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { BudgetLevelPipe } from '../budgets/budget-level.pipe';
import { STATEMENT_STATUS_LABELS } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { addMonths, currentMonth, isMonth, monthLabel } from '../data/month.util';
import { CategoryChartComponent } from './charts/category-chart.component';
import { MonthlyChartComponent } from './charts/monthly-chart.component';

/**
 * Resumo financeiro do mês: saldo, renda x gasto, gráficos (Chart.js via ng2-charts), faturas
 * abertas, orçamentos em alerta e metas. Mês sem dados mostra um aviso no lugar do gráfico.
 */
@Component({
  selector: 'app-finance-dashboard-page',
  imports: [
    BrlPipe,
    BudgetLevelPipe,
    CategoryChartComponent,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MonthlyChartComponent,
    RouterLink,
  ],
  templateUrl: './finance-dashboard.page.html',
  styleUrl: './finance-dashboard.page.scss',
})
export class FinanceDashboardPage {
  private readonly finance = inject(FinanceService);
  private readonly router = inject(Router);

  /** Query param ?month=2026-10. */
  readonly month = input<string>();
  protected readonly mes = computed(() => (isMonth(this.month()) ? this.month()! : currentMonth()));
  protected readonly rotuloMes = computed(() => monthLabel(this.mes()));
  protected readonly rotulosStatus = STATEMENT_STATUS_LABELS;

  protected readonly painel = rxResource({ params: this.mes, stream: ({ params }) => this.finance.dashboard(params) });

  protected readonly semGastos = computed(() => (this.painel.value()?.expenseByCategory.length ?? 0) === 0);
  protected readonly semMovimento = computed(() =>
    (this.painel.value()?.lastSixMonths ?? []).every((p) => Number(p.income) === 0 && Number(p.expense) === 0),
  );
  protected readonly faturasAbertas = computed(() =>
    (this.painel.value()?.cards ?? []).flatMap((card) => card.openStatements.map((s) => ({ card, fatura: s }))),
  );
  protected readonly erro = computed(() =>
    this.painel.error() ? problemMessage(this.painel.error(), 'Não foi possível carregar o resumo.') : null,
  );

  protected mudarMes(delta: number): void {
    this.router.navigate([], { queryParams: { month: addMonths(this.mes(), delta) }, queryParamsHandling: 'merge' });
  }

  /** "2026-10-12" → "12/10". */
  protected diaMes(iso: string): string {
    return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
  }

  protected largura(percentual: string): number {
    return Math.min(100, Number(percentual));
  }
}
