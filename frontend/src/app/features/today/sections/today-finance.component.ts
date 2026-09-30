import { Component, computed, inject, input, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { ScheduledOccurrence } from '../../finance/data/finance.model';
import { FinanceService } from '../../finance/data/finance.service';
import { TransactionFormComponent } from '../../finance/transactions/transaction-form.component';
import { DueItem, FinanceToday } from '../data/today.service';

const DIA = 86_400_000;

/**
 * Seção "Finanças" da tela Hoje: gasto do dia, orçamentos em alerta (vermelho quando estourou),
 * faturas e contas que vencem em até 3 dias e lançamento rápido de gasto. O formulário é o mesmo
 * TransactionFormComponent da feature de finanças, em modo compacto (input `compact`).
 */
@Component({
  selector: 'app-today-finance',
  imports: [BrlPipe, MatButtonModule, MatIconModule, RouterLink, TransactionFormComponent],
  template: `
    <section class="secao" aria-labelledby="hoje-financas">
      <div class="secao__topo">
        <h2 id="hoje-financas" class="secao__titulo">Finanças</h2>
        <a mat-button routerLink="/financas">Ver finanças</a>
      </div>

      <div class="gasto">
        <span>Gasto hoje</span>
        <strong>{{ finance().spentToday | brl }}</strong>
        <button mat-stroked-button type="button" (click)="lancando.set(!lancando())">
          <mat-icon aria-hidden="true">{{ lancando() ? 'close' : 'add' }}</mat-icon>
          {{ lancando() ? 'Fechar' : 'Lançar gasto' }}
        </button>
      </div>

      @if (lancando()) {
        @if (dados.hasValue()) {
          @if (dados.value().contas.length > 0) {
            <app-transaction-form
              [compact]="true"
              [accounts]="dados.value().contas"
              [categories]="dados.value().categorias"
              (saved)="lancado()"
            />
          } @else {
            <p class="vazio">Cadastre uma conta em <a routerLink="/financas/contas">Finanças › Contas</a> para lançar.</p>
          }
        }
      }

      @if (paraConfirmar().length > 0) {
        <ul class="lista confirmar" aria-label="Para confirmar">
          @for (o of paraConfirmar(); track o.id) {
            <li class="conta" [class.conta--atrasada]="o.status === 'OVERDUE'">
              <div class="conta__texto">
                <span class="conta__titulo">{{ o.description ?? (o.type === 'INCOME' ? 'Recebimento' : 'Pagamento') }}</span>
                <span class="conta__detalhe" [class.conta__detalhe--hoje]="o.status === 'OVERDUE'">
                  {{ o.accountName }} · {{ o.status === 'OVERDUE' ? 'atrasado desde ' + diaMes(o.date) : 'para confirmar hoje' }}
                </span>
              </div>
              <span class="conta__valor">{{ o.amount | brl }}</span>
              <button mat-flat-button type="button" [disabled]="confirmando() === o.id" (click)="confirmar(o)">
                {{ o.type === 'INCOME' ? 'Recebi' : 'Paguei' }}
              </button>
            </li>
          }
        </ul>
        <a mat-button routerLink="/financas/agendados" class="mais">Remarcar ou ajustar valor</a>
      }

      @for (b of finance().budgetAlerts; track b.id) {
        <p class="alert" [class.alert--over]="b.level === 'ESTOURADO'" [class.alert--warn]="b.level === 'ATENCAO'" role="status">
          <mat-icon aria-hidden="true">{{ b.level === 'ESTOURADO' ? 'error' : 'warning' }}</mat-icon>
          {{ b.categoryName }}:
          {{ b.level === 'ESTOURADO' ? 'orçamento estourado' : 'perto do limite' }}
          ({{ b.spent | brl }} de {{ b.amount | brl }})
        </p>
      }

      <ul class="lista" aria-label="Vence em breve">
        @for (item of finance().dueSoon; track item.id) {
          <li class="conta">
            <div class="conta__texto">
              @if (item.kind === 'STATEMENT') {
                <a class="conta__titulo" [routerLink]="['/financas/cartoes', item.cardId, 'faturas', item.referenceMonth]">{{ item.title }}</a>
              } @else {
                <a class="conta__titulo" routerLink="/financas/extrato" [queryParams]="{ month: item.dueDate.slice(0, 7) }">{{ item.title }}</a>
              }
              <span class="conta__detalhe" [class.conta__detalhe--hoje]="diasAte(item) === 0">{{ quando(item) }}</span>
            </div>
            <span class="conta__valor">{{ item.amount | brl }}</span>
          </li>
        } @empty {
          <li class="vazio">Nada vencendo nos próximos dias.</li>
        }
      </ul>
    </section>
  `,
  styles: `
    .secao__topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .secao__titulo {
      font: var(--mat-sys-title-large);
      margin: 8px 0;
    }
    .gasto {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 8px;
    }
    .alert {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 6px;
      padding: 8px 12px;
      border-radius: 8px;
    }
    .alert--warn {
      background: #fff8e1;
      color: #795500;
    }
    .alert--over {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .lista {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .conta {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
      margin-bottom: 6px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .conta__texto {
      display: flex;
      flex-direction: column;
      flex: 1;
    }
    .conta__titulo {
      font: var(--mat-sys-title-small);
      color: inherit;
    }
    .conta__detalhe,
    .vazio {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .conta__detalhe--hoje {
      color: var(--mat-sys-error);
      font-weight: 600;
    }
    .conta--atrasada {
      border-left: 4px solid var(--mat-sys-error);
    }
    .confirmar {
      margin-bottom: 4px;
    }
    .mais {
      margin-bottom: 8px;
    }
    .conta__valor {
      font-weight: 600;
    }
  `,
})
export class TodayFinanceComponent {
  private readonly financeApi = inject(FinanceService);
  private readonly snackBar = inject(MatSnackBar);

  /** Hoje segundo a API (fuso do usuário). */
  readonly today = input.required<string>();
  readonly finance = input.required<FinanceToday>();
  /** Um gasto foi lançado: a tela Hoje recarrega (gasto do dia e alertas mudam). */
  readonly changed = output<void>();

  protected readonly lancando = signal(false);

  /** Confirmados nesta tela somem na hora (a tela Hoje recarrega em seguida). */
  private readonly confirmados = signal<ReadonlySet<string>>(new Set());
  protected readonly paraConfirmar = computed(() =>
    (this.finance().toConfirm ?? []).filter((o) => o.id && !this.confirmados().has(o.id)),
  );
  protected readonly confirmando = signal<string | null>(null);

  /** Contas e categorias só são buscadas quando o formulário abre. */
  protected readonly dados = rxResource({
    params: () => (this.lancando() ? true : undefined),
    stream: () => forkJoin({ contas: this.financeApi.listAccounts(), categorias: this.financeApi.listCategories() }),
  });

  private readonly hojeMs = computed(() => Date.parse(this.today()));

  protected diasAte(item: DueItem): number {
    return Math.round((Date.parse(item.dueDate) - this.hojeMs()) / DIA);
  }

  protected quando(item: DueItem): string {
    const dias = this.diasAte(item);
    return dias <= 0 ? 'vence hoje' : dias === 1 ? 'vence amanhã' : `vence em ${dias} dias`;
  }

  /** "Recebi"/"Paguei" rápido: valor previsto e data de hoje (ajustes ficam na tela Agendados). */
  protected async confirmar(o: ScheduledOccurrence): Promise<void> {
    this.confirmando.set(o.id);
    try {
      await firstValueFrom(this.financeApi.confirmOccurrence(o.id!));
      this.confirmados.update((ids) => new Set([...ids, o.id!]));
      this.changed.emit();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível confirmar.'), 'OK', { duration: 5000 });
    } finally {
      this.confirmando.set(null);
    }
  }

  protected diaMes(iso: string): string {
    return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
  }

  protected lancado(): void {
    this.lancando.set(false);
    this.changed.emit();
  }
}
