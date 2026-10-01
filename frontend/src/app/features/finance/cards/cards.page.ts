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
import { Card, Recurring, Statement } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { todayIso } from '../data/month.util';
import { CardFormData, CardFormDialog } from './card-form.dialog';
import { RecurringFormData, RecurringFormDialog } from '../recurring/recurring-form.dialog';
import { CardPurchaseFormComponent } from './card-purchase-form.component';
import { statementMonthFor } from './statement-period';

interface CardView {
  card: Card;
  /** Fatura em que as compras de hoje caem. */
  current: Statement;
  used: string;
  usedPercent: number;
}

/**
 * Cartões com limite usado e disponível, atalho para a fatura atual, lançamento de compra e as
 * assinaturas do cartão (agendados de gasto no cartão), com o total por mês e "Nova assinatura".
 */
@Component({
  selector: 'app-cards-page',
  imports: [
    BrlPipe,
    CardPurchaseFormComponent,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
  ],
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
            <span class="cartao__dias"
              >Fecha dia {{ item.card.closingDay }} · vence dia {{ item.card.dueDay }}</span
            >
          </div>
          <button
            mat-icon-button
            type="button"
            [attr.aria-label]="'Editar ' + item.card.name"
            (click)="editar(item.card)"
          >
            <mat-icon aria-hidden="true">edit</mat-icon>
          </button>
        </header>

        <mat-progress-bar
          mode="determinate"
          [value]="item.usedPercent"
          [attr.aria-label]="'Limite usado de ' + item.card.name"
        />
        <div class="limite">
          <span
            >Usado <strong>{{ item.used | brl }}</strong></span
          >
          <span class="limite__disponivel"
            >Disponível <strong>{{ item.current.availableLimit | brl }}</strong></span
          >
          <span class="limite__total">de {{ item.card.creditLimit | brl }}</span>
        </div>

        <div class="acoes">
          <a
            mat-stroked-button
            [routerLink]="[item.card.id, 'faturas', item.current.referenceMonth]"
          >
            Fatura atual · {{ item.current.total | brl }}
          </a>
          <button mat-button type="button" (click)="alternarCompra(item.card.id)">
            <mat-icon aria-hidden="true">add_shopping_cart</mat-icon>
            Nova compra
          </button>
          <button mat-button type="button" (click)="assinatura(item.card.id)">
            <mat-icon aria-hidden="true">autorenew</mat-icon>
            Nova assinatura
          </button>
        </div>

        @let lista = assinaturasDo(item.card.id);
        @if (lista.length) {
          <section class="assinaturas" [attr.aria-label]="'Assinaturas do ' + item.card.name">
            <h3 class="assinaturas__titulo">
              Assinaturas
              <span class="assinaturas__total">{{ totalMensal(lista) | brl }}/mês</span>
            </h3>
            <ul class="assinaturas__lista">
              @for (a of lista; track a.id) {
                <li>
                  <button type="button" class="assinatura" (click)="assinatura(item.card.id, a)">
                    <span class="assinatura__nome">{{ a.description ?? 'Assinatura' }}</span>
                    <span class="assinatura__dia">{{ quando(a) }}</span>
                    <span class="assinatura__valor">{{ a.amount | brl }}</span>
                  </button>
                </li>
              }
            </ul>
          </section>
        }

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
    .assinaturas__titulo {
      display: flex;
      justify-content: space-between;
      margin: 8px 0 4px;
      font: var(--mat-sys-title-small);
    }
    .assinaturas__total {
      color: var(--mat-sys-on-surface-variant);
    }
    .assinaturas__lista {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .assinatura {
      display: flex;
      gap: 12px;
      width: 100%;
      padding: 6px 0;
      border: 0;
      border-top: 1px solid var(--mat-sys-outline-variant);
      background: none;
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .assinatura__nome {
      flex: 1;
    }
    .assinatura__dia {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .assinatura__valor {
      font-weight: 600;
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
  /** Agendados de gasto no cartão que ainda vão cobrar (nextDate null = já terminou). */
  private readonly agendados = rxResource({ stream: () => this.finance.listRecurring() });
  private readonly porCartao = computed(() => {
    const mapa = new Map<string, Recurring[]>();
    // value() lança com o resource em erro: sem a lista, o cartão aparece sem as assinaturas
    for (const r of this.agendados.hasValue() ? this.agendados.value() : []) {
      if (r.cardId && r.nextDate) {
        mapa.set(r.cardId, [...(mapa.get(r.cardId) ?? []), r]);
      }
    }
    return mapa;
  });

  /** Id do cartão com o formulário de compra aberto. */
  protected readonly comprando = signal<string | null>(null);

  protected readonly erro = computed(() =>
    this.cartoes.error()
      ? problemMessage(this.cartoes.error(), 'Não foi possível carregar os cartões.')
      : null,
  );

  protected assinaturasDo(cardId: string): Recurring[] {
    return this.porCartao().get(cardId) ?? [];
  }

  protected totalMensal(lista: Recurring[]): string {
    return centsToDecimal(lista.reduce((soma, r) => soma + toCents(r.amount), 0n));
  }

  /** "todo dia 7 · próxima 07/11". */
  protected quando(r: Recurring): string {
    const regra =
      r.ruleType === 'DAY_OF_MONTH'
        ? `todo dia ${r.dayOfMonth}`
        : r.ruleType === 'BUSINESS_DAY'
          ? `${r.businessDay}º dia útil`
          : 'último dia útil';
    return r.nextDate
      ? `${regra} · próxima ${r.nextDate.slice(8, 10)}/${r.nextDate.slice(5, 7)}`
      : regra;
  }

  /** Nova assinatura neste cartão, ou edita a existente. */
  protected assinatura(cardId: string, recurring?: Recurring): void {
    this.dialog
      .open<RecurringFormDialog, RecurringFormData, boolean>(RecurringFormDialog, {
        data: recurring ? { recurring } : { cardId },
      })
      .afterClosed()
      .subscribe((mudou) => {
        if (mudou) {
          this.agendados.reload();
          this.cartoes.reload();
        }
      });
  }

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
  return {
    card,
    current,
    used: centsToDecimal(usado),
    usedPercent: Math.min(100, Math.max(0, percentual)),
  };
}
