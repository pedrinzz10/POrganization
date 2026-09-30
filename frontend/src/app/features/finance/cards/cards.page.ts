import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { centsToDecimal, toCents } from '../../../shared/money-input/money-input.directive';
import { Card, Statement } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { todayIso } from '../data/month.util';
import { CardFormData, CardFormDialog } from './card-form.dialog';
import { CardPurchaseFormComponent } from './card-purchase-form.component';
import { statementMonthFor } from './statement-period';

interface CardView {
  card: Card;
  /** Fatura em que as compras de hoje caem. */
  current: Statement;
  used: string;
  usedPercent: number;
}

/** Cartões com limite usado e disponível, atalho para a fatura atual e lançamento de compra. */
@Component({
  selector: 'app-cards-page',
  imports: [BrlPipe, CardPurchaseFormComponent, MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink],
  template: `
    <div class="topo">
      <button mat-flat-button type="button" (click)="editar()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Novo cartão
      </button>
    </div>

    @if (cartoes.isLoading() && !cartoes.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    @for (item of cartoes.value() ?? []; track item.card.id) {
      <article class="cartao" [attr.aria-label]="item.card.name">
        <header class="cartao__topo">
          <div>
            <h2 class="cartao__nome">{{ item.card.name }}</h2>
            <span class="cartao__dias">Fecha dia {{ item.card.closingDay }} · vence dia {{ item.card.dueDay }}</span>
          </div>
          <button mat-icon-button type="button" [attr.aria-label]="'Editar ' + item.card.name" (click)="editar(item.card)">
            <mat-icon aria-hidden="true">edit</mat-icon>
          </button>
        </header>

        <mat-progress-bar mode="determinate" [value]="item.usedPercent" [attr.aria-label]="'Limite usado de ' + item.card.name" />
        <div class="limite">
          <span>Usado <strong>{{ item.used | brl }}</strong></span>
          <span class="limite__disponivel">Disponível <strong>{{ item.current.availableLimit | brl }}</strong></span>
          <span class="limite__total">de {{ item.card.creditLimit | brl }}</span>
        </div>

        <div class="acoes">
          <a mat-stroked-button [routerLink]="[item.card.id, 'faturas', item.current.referenceMonth]">
            Fatura atual · {{ item.current.total | brl }}
          </a>
          <button mat-button type="button" (click)="alternarCompra(item.card.id)">
            <mat-icon aria-hidden="true">add_shopping_cart</mat-icon>
            Nova compra
          </button>
        </div>

        @if (comprando() === item.card.id) {
          <app-card-purchase-form
            [card]="item.card"
            [categories]="categorias.value() ?? []"
            (saved)="compraLancada()"
            (cancelled)="comprando.set(null)"
          />
        }
      </article>
    } @empty {
      @if (cartoes.hasValue()) {
        <p class="vazio">Nenhum cartão ainda. Cadastre o primeiro!</p>
      }
    }
  `,
  styles: `
    .topo {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 12px;
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .cartao {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 16px;
      margin-bottom: 12px;
      border-radius: 16px;
      background: var(--mat-sys-surface-container-low);
    }
    .cartao__topo {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    .cartao__nome {
      font: var(--mat-sys-title-medium);
      margin: 0;
    }
    .cartao__dias,
    .limite__total {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .limite {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      align-items: baseline;
    }
    .acoes {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .vazio {
      padding: 24px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class CardsPage {
  private readonly finance = inject(FinanceService);
  private readonly dialog = inject(MatDialog);

  /** Cartões ativos, cada um com a fatura em que as compras de hoje caem (traz o limite disponível). */
  protected readonly cartoes = rxResource({
    stream: () =>
      this.finance.listCards().pipe(
        map((cards) => cards.filter((c) => !c.archived)),
        switchMap((cards) =>
          cards.length === 0
            ? of([] as CardView[])
            : forkJoin(
                cards.map((card) =>
                  this.finance
                    .statement(card.id, statementMonthFor(card.closingDay, card.dueDay, todayIso()))
                    .pipe(map((current) => toView(card, current))),
                ),
              ),
        ),
      ),
  });
  protected readonly categorias = rxResource({ stream: () => this.finance.listCategories() });

  /** Id do cartão com o formulário de compra aberto. */
  protected readonly comprando = signal<string | null>(null);

  protected readonly erro = computed(() =>
    this.cartoes.error() ? problemMessage(this.cartoes.error(), 'Não foi possível carregar os cartões.') : null,
  );

  protected alternarCompra(cardId: string): void {
    this.comprando.update((atual) => (atual === cardId ? null : cardId));
  }

  protected compraLancada(): void {
    this.comprando.set(null);
    this.cartoes.reload();
  }

  protected editar(card?: Card): void {
    this.dialog
      .open<CardFormDialog, CardFormData, boolean>(CardFormDialog, { data: { card } })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.cartoes.reload();
        }
      });
  }
}

function toView(card: Card, current: Statement): CardView {
  const limite = toCents(card.creditLimit);
  const usado = limite - toCents(current.availableLimit);
  const percentual = limite > 0n ? Number((usado * 10000n) / limite) / 100 : 0;
  return { card, current, used: centsToDecimal(usado), usedPercent: Math.min(100, Math.max(0, percentual)) };
}
