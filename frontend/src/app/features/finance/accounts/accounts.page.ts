import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { centsToDecimal, toCents } from '../../../shared/money-input/money-input.directive';
import { Account, ACCOUNT_TYPE_LABELS } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { AccountFormData, AccountFormDialog } from './account-form.dialog';

/** Contas com o saldo atual (inicial + lançamentos pagos) e o total. Arquivadas ficam no fim, apagadas. */
@Component({
  selector: 'app-accounts-page',
  imports: [BrlPipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="topo">
      <div>
        <span class="total__rotulo">Saldo total</span>
        <span class="total__valor">{{ total() | brl }}</span>
      </div>
      <button mat-flat-button type="button" (click)="abrir()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Nova conta
      </button>
    </div>

    @if (contas.isLoading() && !contas.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    <ul class="lista">
      @for (conta of lista(); track conta.id) {
        <li class="conta" [class.conta--arquivada]="conta.archived">
          <button type="button" class="conta__nome" (click)="abrir(conta)">{{ conta.name }}</button>
          <span class="conta__tipo">{{ rotulos[conta.type] }}{{ conta.archived ? ' · arquivada' : '' }}</span>
          <span class="conta__saldo" [class.negativo]="conta.balance.startsWith('-')">{{ conta.balance | brl }}</span>
        </li>
      } @empty {
        @if (contas.hasValue()) {
          <li class="vazio">Nenhuma conta ainda. Cadastre a primeira!</li>
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
    .total__rotulo {
      display: block;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }
    .total__valor {
      font: var(--mat-sys-headline-small);
    }
    .erro,
    .negativo {
      color: var(--mat-sys-error);
    }
    .lista {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .conta {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      margin-bottom: 6px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .conta--arquivada {
      opacity: 0.6;
    }
    .conta__nome {
      flex: 1;
      text-align: left;
      font: var(--mat-sys-title-small);
      background: none;
      border: none;
      padding: 0;
      color: inherit;
      cursor: pointer;
    }
    .conta__tipo {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .conta__saldo {
      font-weight: 600;
      min-width: 110px;
      text-align: right;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class AccountsPage {
  private readonly finance = inject(FinanceService);
  private readonly dialog = inject(MatDialog);

  protected readonly contas = rxResource({ stream: () => this.finance.listAccounts(true) });
  protected readonly rotulos = ACCOUNT_TYPE_LABELS;

  protected readonly lista = computed(() =>
    this.contas.hasValue() ? [...this.contas.value()].sort((a, b) => Number(a.archived) - Number(b.archived)) : [],
  );

  /** Soma em centavos das contas ativas. */
  protected readonly total = computed(() =>
    centsToDecimal(this.lista().filter((c) => !c.archived).reduce((soma, c) => soma + toCents(c.balance), 0n)),
  );

  protected readonly erro = computed(() =>
    this.contas.error() ? problemMessage(this.contas.error(), 'Não foi possível carregar as contas.') : null,
  );

  protected abrir(conta?: Account): void {
    this.dialog
      .open<AccountFormDialog, AccountFormData, boolean>(AccountFormDialog, { data: { account: conta } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.contas.reload();
        }
      });
  }
}
