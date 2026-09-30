import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { BudgetStatus } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { addMonths, currentMonth, isMonth, monthLabel } from '../data/month.util';
import { BudgetLevelPipe } from './budget-level.pipe';

/**
 * Orçamentos do mês: uma barra por categoria, verde até 80%, amarela até 100% e vermelha
 * a partir daí (pipe budgetLevel). O mês fica no query param ?month=.
 */
@Component({
  selector: 'app-budgets-page',
  imports: [
    BrlPipe,
    BudgetLevelPipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MoneyInputDirective,
    ReactiveFormsModule,
  ],
  template: `
    <div class="mes">
      <button mat-icon-button type="button" aria-label="Mês anterior" (click)="mudarMes(-1)">
        <mat-icon aria-hidden="true">chevron_left</mat-icon>
      </button>
      <h2 class="mes__rotulo">{{ rotuloMes() }}</h2>
      <button mat-icon-button type="button" aria-label="Próximo mês" (click)="mudarMes(1)">
        <mat-icon aria-hidden="true">chevron_right</mat-icon>
      </button>
    </div>

    @if (orcamentos.isLoading() && !orcamentos.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    <ul class="lista">
      @for (b of orcamentos.value() ?? []; track b.id) {
        <li [class]="'orcamento ' + (b.level | budgetLevel)">
          <div class="orcamento__topo">
            <span class="orcamento__nome">{{ b.categoryName }}</span>
            <span class="orcamento__nivel">{{ b.level | budgetLevel: 'label' }}</span>
            <button mat-icon-button type="button" [attr.aria-label]="'Excluir orçamento de ' + b.categoryName" (click)="excluir(b)">
              <mat-icon aria-hidden="true">delete</mat-icon>
            </button>
          </div>
          <div class="barra" role="progressbar" [attr.aria-valuenow]="b.percent" aria-valuemin="0" aria-valuemax="100"
               [attr.aria-label]="'Gasto em ' + b.categoryName">
            <div class="barra__cheia" [style.width.%]="largura(b)"></div>
          </div>
          <div class="orcamento__valores">
            <span>{{ b.spent | brl }} de {{ b.amount | brl }} · {{ b.percent.replace('.', ',') }}%</span>
            <span>{{ b.month ? 'só neste mês' : 'todo mês' }}</span>
          </div>
        </li>
      } @empty {
        @if (orcamentos.hasValue()) {
          <li class="vazio">Nenhum orçamento para este mês.</li>
        }
      }
    </ul>

    <form class="novo" [formGroup]="form" (ngSubmit)="criar()" aria-label="Novo orçamento">
      <mat-form-field>
        <mat-label>Categoria</mat-label>
        <mat-select formControlName="categoryId">
          @for (c of categoriasLivres(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Limite</mat-label>
        <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
      </mat-form-field>
      <mat-checkbox formControlName="onlyThisMonth">Só neste mês</mat-checkbox>
      <button mat-flat-button type="submit" [disabled]="salvando()">Adicionar</button>
    </form>
  `,
  styles: `
    .mes {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .mes__rotulo {
      font: var(--mat-sys-title-large);
      margin: 0;
      min-width: 180px;
      text-align: center;
    }
    .mes__rotulo::first-letter {
      text-transform: uppercase;
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .lista {
      list-style: none;
      padding: 0;
      margin: 12px 0;
    }
    .orcamento {
      padding: 12px;
      margin-bottom: 8px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
      --cor-barra: #43a047;
    }
    .budget--warn {
      --cor-barra: #f9a825;
    }
    .budget--over {
      --cor-barra: #e53935;
    }
    .orcamento__topo {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .orcamento__nome {
      flex: 1;
      font: var(--mat-sys-title-small);
    }
    .orcamento__nivel {
      font: var(--mat-sys-label-medium);
      color: var(--cor-barra);
    }
    .barra {
      height: 10px;
      border-radius: 5px;
      background: var(--mat-sys-surface-container-highest);
      overflow: hidden;
    }
    .barra__cheia {
      height: 100%;
      background: var(--cor-barra);
    }
    .orcamento__valores {
      display: flex;
      justify-content: space-between;
      margin-top: 4px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .novo {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 12px;
    }
    .vazio {
      padding: 16px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class BudgetsPage {
  private readonly finance = inject(FinanceService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** Query param ?month=2026-10. */
  readonly month = input<string>();
  protected readonly mes = computed(() => (isMonth(this.month()) ? this.month()! : currentMonth()));
  protected readonly rotuloMes = computed(() => monthLabel(this.mes()));

  protected readonly orcamentos = rxResource({ params: this.mes, stream: ({ params }) => this.finance.budgets(params) });
  private readonly categorias = rxResource({ stream: () => this.finance.listCategories() });

  /** Categorias de gasto que ainda não têm orçamento neste mês. */
  protected readonly categoriasLivres = computed(() => {
    const usadas = new Set((this.orcamentos.value() ?? []).map((b) => b.categoryId));
    return (this.categorias.value() ?? []).filter((c) => c.kind === 'EXPENSE' && !usadas.has(c.id));
  });

  protected readonly form = inject(FormBuilder).group({
    categoryId: [null as string | null, Validators.required],
    amount: [null as string | null, Validators.required],
    onlyThisMonth: [false],
  });
  protected readonly salvando = signal(false);

  protected readonly erro = computed(() =>
    this.orcamentos.error() ? problemMessage(this.orcamentos.error(), 'Não foi possível carregar os orçamentos.') : null,
  );

  /** Largura da barra: até 100% (o estouro aparece na cor e no texto). */
  protected largura(b: BudgetStatus): number {
    return Math.min(100, Number(b.percent));
  }

  protected mudarMes(delta: number): void {
    this.router.navigate([], { queryParams: { month: addMonths(this.mes(), delta) }, queryParamsHandling: 'merge' });
  }

  protected async criar(): Promise<void> {
    const v = this.form.getRawValue();
    if (!v.categoryId || !v.amount || this.salvando()) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    try {
      await firstValueFrom(
        this.finance.createBudget({ categoryId: v.categoryId, amount: v.amount, month: v.onlyThisMonth ? this.mes() : null }),
      );
      this.form.reset({ categoryId: null, amount: null, onlyThisMonth: false });
      this.orcamentos.reload();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível criar o orçamento.'), 'OK', { duration: 5000 });
    } finally {
      this.salvando.set(false);
    }
  }

  protected async excluir(b: BudgetStatus): Promise<void> {
    try {
      await firstValueFrom(this.finance.deleteBudget(b.id));
      this.orcamentos.reload();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível excluir.'), 'OK', { duration: 5000 });
    }
  }
}
