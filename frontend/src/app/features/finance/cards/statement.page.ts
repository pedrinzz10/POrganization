import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../../shared/confirm-dialog/confirm.dialog';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { STATEMENT_STATUS_LABELS, StatementItem } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { addMonths, currentMonth, isMonth, monthLabel } from '../data/month.util';

/**
 * Fatura de um cartão num mês: /financas/cartoes/:id/faturas/:mes. Os parâmetros da rota chegam
 * como input() (withComponentInputBinding). Pagar pede confirmação e cria o gasto na conta de pagamento.
 */
@Component({
  selector: 'app-statement-page',
  imports: [BrlPipe, MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink],
  template: `
    <a mat-button routerLink="/financas/cartoes" class="voltar">
      <mat-icon aria-hidden="true">arrow_back</mat-icon>
      Cartões
    </a>

    <div class="mes">
      <a
        mat-icon-button
        [routerLink]="['/financas/cartoes', id(), 'faturas', mesAnterior()]"
        aria-label="Fatura anterior"
      >
        <mat-icon aria-hidden="true">chevron_left</mat-icon>
      </a>
      <h2 class="mes__rotulo">{{ nomeCartao() }} · {{ rotuloMes() }}</h2>
      <a
        mat-icon-button
        [routerLink]="['/financas/cartoes', id(), 'faturas', proximoMes()]"
        aria-label="Próxima fatura"
      >
        <mat-icon aria-hidden="true">chevron_right</mat-icon>
      </a>
    </div>

    @if (fatura.isLoading() && !fatura.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    @if (fatura.hasValue()) {
      @let f = fatura.value();
      <section class="cabecalho">
        <span [class]="'selo selo--' + f.status.toLowerCase()">{{ rotulosStatus[f.status] }}</span>
        <div class="total">
          <span class="total__rotulo">Total</span>
          <span class="total__valor">{{ f.total | brl }}</span>
        </div>
        <div class="datas">
          <span>Fecha em {{ data(f.closingDate) }}</span>
          <span>Vence em {{ data(f.dueDate) }}</span>
          <span>Limite disponível {{ f.availableLimit | brl }}</span>
        </div>
        @if (podePagar()) {
          <button mat-flat-button type="button" [disabled]="pagando()" (click)="pagar()">
            Pagar
          </button>
        }
      </section>

      <ul class="itens" aria-label="Compras da fatura">
        @for (item of f.items; track item.id) {
          <li class="item">
            <span class="item__data">{{ data(item.date).slice(0, 5) }}</span>
            <span class="item__descricao">
              {{ item.description ?? 'Compra' }}
              @if (item.recurringId) {
                <span class="ds-chip">
                  <mat-icon class="item__icone" aria-hidden="true">autorenew</mat-icon>
                  assinatura
                </span>
              }
            </span>
            <span class="item__valor">{{ item.amount | brl }}</span>
            @if (item.purchaseId && f.status !== 'PAID') {
              <button
                mat-icon-button
                type="button"
                [attr.aria-label]="'Excluir compra ' + (item.description ?? '')"
                (click)="excluirCompra(item)"
              >
                <mat-icon aria-hidden="true">delete</mat-icon>
              </button>
            }
          </li>
        } @empty {
          <li class="vazio">Nenhuma compra nesta fatura.</li>
        }
      </ul>
    }
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
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .cabecalho {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px 24px;
      padding: 16px;
      margin: 12px 0;
      border-radius: 16px;
      background: var(--mat-sys-surface-container-low);
    }
    .selo {
      padding: 2px 10px;
      border-radius: 12px;
      font: var(--mat-sys-label-large);
    }
    .selo--open {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .selo--closed {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .selo--paid {
      background: #c8e6c9;
      color: #1b5e20;
    }
    .total {
      display: flex;
      flex-direction: column;
    }
    .total__rotulo {
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }
    .total__valor {
      font: var(--mat-sys-headline-small);
    }
    .datas {
      display: flex;
      flex-direction: column;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
      flex: 1;
    }
    .itens {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 4px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .item__data {
      width: 44px;
      color: var(--mat-sys-on-surface-variant);
    }
    .item__descricao {
      flex: 1;
    }
    .item__icone {
      width: 14px;
      height: 14px;
      font-size: 14px;
      margin-right: 2px;
    }
    .item__valor {
      font-weight: 600;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class StatementPage {
  private readonly finance = inject(FinanceService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  // Parâmetros da rota
  readonly id = input.required<string>();
  readonly mes = input.required<string>();

  protected readonly rotulosStatus = STATEMENT_STATUS_LABELS;
  private readonly mesValido = computed(() => (isMonth(this.mes()) ? this.mes() : currentMonth()));
  protected readonly rotuloMes = computed(() => monthLabel(this.mesValido()));
  protected readonly mesAnterior = computed(() => addMonths(this.mesValido(), -1));
  protected readonly proximoMes = computed(() => addMonths(this.mesValido(), 1));

  protected readonly fatura = rxResource({
    params: () => ({ id: this.id(), mes: this.mesValido() }),
    stream: ({ params }) => this.finance.statement(params.id, params.mes),
  });
  private readonly cartoes = rxResource({ stream: () => this.finance.listCards() });
  protected readonly nomeCartao = computed(
    () => this.cartoes.value()?.find((c) => c.id === this.id())?.name ?? 'Cartão',
  );

  protected readonly pagando = signal(false);
  protected readonly podePagar = computed(() => {
    const f = this.fatura.value();
    return !!f && !!f.id && f.status !== 'PAID' && Number(f.total) > 0;
  });
  protected readonly erro = computed(() =>
    this.fatura.error()
      ? problemMessage(this.fatura.error(), 'Não foi possível carregar a fatura.')
      : null,
  );

  /** "2026-10-12" → "12/10/2026". */
  protected data(iso: string): string {
    return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
  }

  protected async pagar(): Promise<void> {
    const f = this.fatura.value()!;
    const confirmou = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data: {
            title: 'Pagar fatura?',
            message: `Vai lançar um gasto de ${new BrlPipe().transform(f.total)} na conta de pagamento do cartão.`,
            confirmLabel: 'Pagar',
          },
        })
        .afterClosed(),
    );
    if (!confirmou) {
      return;
    }
    this.pagando.set(true);
    try {
      this.fatura.set(await firstValueFrom(this.finance.payStatement(f.id!)));
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível pagar a fatura.'), 'OK', {
        duration: 5000,
      });
    } finally {
      this.pagando.set(false);
    }
  }

  protected async excluirCompra(item: StatementItem): Promise<void> {
    const parcelas =
      item.installmentCount && item.installmentCount > 1
        ? ` e as ${item.installmentCount} parcelas`
        : '';
    const confirmou = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data: {
            title: 'Excluir compra?',
            message: `Exclui a compra${parcelas} de todas as faturas.`,
            confirmLabel: 'Excluir',
          },
        })
        .afterClosed(),
    );
    if (!confirmou) {
      return;
    }
    try {
      await firstValueFrom(this.finance.deletePurchase(item.purchaseId!));
      this.fatura.reload();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível excluir a compra.'), 'OK', {
        duration: 5000,
      });
    }
  }
}
