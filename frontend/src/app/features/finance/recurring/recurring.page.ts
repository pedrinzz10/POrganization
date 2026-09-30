import { Component, computed, inject, output } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { Recurring } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { monthLabel } from '../data/month.util';
import { RecurringFormData, RecurringFormDialog } from './recurring-form.dialog';

/** Gastos e rendas fixos: cadastrados uma vez, aparecem todo mês no extrato como pendentes. */
@Component({
  selector: 'app-recurring-page',
  imports: [BrlPipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="topo">
      <p class="dica">Salário, aluguel, assinaturas: cadastre a regra uma vez e cada mês aparece para você confirmar.</p>
      <button mat-flat-button type="button" (click)="editar()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Novo agendado
      </button>
    </div>

    @if (fixos.isLoading() && !fixos.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    <ul class="lista">
      @for (fixo of fixos.value() ?? []; track fixo.id) {
        <li class="fixo">
          <span class="fixo__dia">{{ quando(fixo) }}</span>
          <button type="button" class="fixo__texto" (click)="editar(fixo)">
            <span class="fixo__nome">{{ fixo.description ?? nomeCategoria(fixo.categoryId) }}</span>
            <span class="fixo__detalhe">{{ nomeCategoria(fixo.categoryId) }} · {{ destino(fixo) }} · {{ periodo(fixo) }}</span>
          </button>
          <span class="fixo__valor" [class.entra]="fixo.type === 'INCOME'" [class.sai]="fixo.type === 'EXPENSE'">
            {{ (fixo.type === 'INCOME' ? fixo.amount : '-' + fixo.amount) | brl: true }}
          </span>
        </li>
      } @empty {
        @if (fixos.hasValue()) {
          <li class="vazio">Nenhum agendado cadastrado.</li>
        }
      }
    </ul>
  `,
  styles: `
    .topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }
    .dica {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .erro,
    .sai {
      color: var(--mat-sys-error);
    }
    .entra {
      color: #2e7d32;
    }
    .lista {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .fixo {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 4px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .fixo__dia {
      width: 150px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }
    .fixo__texto {
      flex: 1;
      display: flex;
      flex-direction: column;
      text-align: left;
      background: none;
      border: none;
      padding: 0;
      color: inherit;
      cursor: pointer;
    }
    .fixo__detalhe {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .fixo__valor {
      font-weight: 600;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class RecurringPage {
  private readonly finance = inject(FinanceService);
  private readonly dialog = inject(MatDialog);

  /** Uma regra mudou: a lista do mês (Agendados) recarrega. */
  readonly changed = output<void>();

  protected readonly fixos = rxResource({ stream: () => this.finance.listRecurring() });
  private readonly contas = rxResource({ stream: () => this.finance.listAccounts(true) });
  private readonly cartoes = rxResource({ stream: () => this.finance.listCards() });
  private readonly categorias = rxResource({ stream: () => this.finance.listCategories() });

  protected readonly erro = computed(() =>
    this.fixos.error() ? problemMessage(this.fixos.error(), 'Não foi possível carregar os fixos.') : null,
  );

  protected nomeCategoria(id: string): string {
    return this.categorias.value()?.find((c) => c.id === id)?.name ?? '';
  }

  /** "5º dia útil", "último dia útil" ou "dia 20" (com o ajuste), e a próxima data. */
  protected quando(fixo: Recurring): string {
    const regra =
      fixo.ruleType === 'BUSINESS_DAY'
        ? `${fixo.businessDay}º dia útil`
        : fixo.ruleType === 'LAST_BUSINESS_DAY'
          ? 'último dia útil'
          : `dia ${fixo.dayOfMonth}${fixo.adjustment === 'ANTICIPATE' ? ' (antecipa)' : fixo.adjustment === 'POSTPONE' ? ' (adia)' : ''}`;
    return fixo.nextDate ? `${regra} · próximo ${fixo.nextDate.slice(8, 10)}/${fixo.nextDate.slice(5, 7)}` : regra;
  }

  protected destino(fixo: Recurring): string {
    if (fixo.cardId) {
      return `Cartão ${this.cartoes.value()?.find((c) => c.id === fixo.cardId)?.name ?? ''}`.trim();
    }
    return this.contas.value()?.find((c) => c.id === fixo.accountId)?.name ?? 'Conta';
  }

  protected periodo(fixo: Recurring): string {
    return fixo.endMonth ? `de ${monthLabel(fixo.startMonth)} a ${monthLabel(fixo.endMonth)}` : `desde ${monthLabel(fixo.startMonth)}`;
  }

  protected editar(recurring?: Recurring): void {
    this.dialog
      .open<RecurringFormDialog, RecurringFormData, boolean>(RecurringFormDialog, { data: { recurring } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.fixos.reload();
          this.changed.emit();
        }
      });
  }
}
