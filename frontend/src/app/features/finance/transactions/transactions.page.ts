import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { Transaction, TransactionFilters, TransactionType } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { addMonths, currentMonth, isMonth, monthLabel } from '../data/month.util';
import { TransactionFormComponent } from './transaction-form.component';

const TIPOS: TransactionType[] = ['EXPENSE', 'INCOME', 'TRANSFER'];

/**
 * Extrato do mês. Mês e filtros vivem nos query params (?month=2026-10&category=...): o link é
 * compartilhável e recarregar a página mantém tudo. Com withComponentInputBinding() cada query
 * param chega como input() do componente.
 */
@Component({
  selector: 'app-transactions-page',
  imports: [
    BrlPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    TransactionFormComponent,
  ],
  templateUrl: './transactions.page.html',
  styleUrl: './transactions.page.scss',
})
export class TransactionsPage {
  private readonly finance = inject(FinanceService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  // Query params
  readonly month = input<string>();
  readonly account = input<string>();
  readonly category = input<string>();
  readonly tag = input<string>();
  readonly type = input<string>();

  protected readonly mes = computed(() => (isMonth(this.month()) ? this.month()! : currentMonth()));
  protected readonly rotuloMes = computed(() => monthLabel(this.mes()));
  protected readonly tipos = TIPOS;
  protected readonly rotulosTipo: Record<TransactionType, string> = { EXPENSE: 'Gastos', INCOME: 'Rendas', TRANSFER: 'Transferências' };

  private readonly filtros = computed<TransactionFilters>(() => ({
    month: this.mes(),
    accountId: this.account() || undefined,
    categoryId: this.category() || undefined,
    tagId: this.tag() || undefined,
    type: TIPOS.includes(this.type() as TransactionType) ? (this.type() as TransactionType) : undefined,
  }));

  protected readonly transacoes = rxResource({ params: this.filtros, stream: ({ params }) => this.finance.listTransactions(params) });
  protected readonly resumo = rxResource({ params: this.mes, stream: ({ params }) => this.finance.summary(params) });
  protected readonly contas = rxResource({ stream: () => this.finance.listAccounts() });
  protected readonly categorias = rxResource({ stream: () => this.finance.listCategories() });
  protected readonly tags = rxResource({ stream: () => this.finance.listTags() });

  protected readonly listaContas = computed(() => (this.contas.hasValue() ? this.contas.value() : []));
  protected readonly listaCategorias = computed(() => (this.categorias.hasValue() ? this.categorias.value() : []));
  protected readonly listaTags = computed(() => (this.tags.hasValue() ? this.tags.value() : []));

  protected readonly carregando = computed(() => this.transacoes.isLoading());
  protected readonly erro = computed(() =>
    this.transacoes.error() ? problemMessage(this.transacoes.error(), 'Não foi possível carregar o extrato.') : null,
  );
  protected readonly temFiltro = computed(() => {
    const f = this.filtros();
    return !!(f.accountId || f.categoryId || f.tagId || f.type);
  });

  protected mudarMes(delta: number): void {
    this.navegar({ month: addMonths(this.mes(), delta) });
  }

  /** Troca um filtro nos query params; vazio tira o parâmetro da URL. */
  protected filtrar(chave: 'account' | 'category' | 'tag' | 'type', valor: string | null): void {
    this.navegar({ [chave]: valor || null });
  }

  protected limparFiltros(): void {
    this.navegar({ account: null, category: null, tag: null, type: null });
  }

  /** Depois de lançar ou excluir: extrato, resumo e saldos recarregam sem recarregar a página. */
  protected recarregar(): void {
    this.transacoes.reload();
    this.resumo.reload();
    this.contas.reload();
  }

  protected async excluir(t: Transaction): Promise<void> {
    try {
      await firstValueFrom(this.finance.deleteTransaction(t.id));
      this.recarregar();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível excluir.'), 'OK', { duration: 5000 });
    }
  }

  /** Valor com sinal: renda e transferência recebida somam, o resto subtrai. */
  protected valorComSinal(t: Transaction): string {
    const entra = t.type === 'INCOME' || t.transferDirection === 'IN';
    return entra ? t.amount : `-${t.amount}`;
  }

  protected titulo(t: Transaction): string {
    if (t.description) return t.description;
    if (t.type === 'TRANSFER') return t.transferDirection === 'IN' ? 'Transferência recebida' : 'Transferência enviada';
    return t.categoryName ?? 'Sem descrição';
  }

  private navegar(queryParams: Record<string, string | null>): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }
}
