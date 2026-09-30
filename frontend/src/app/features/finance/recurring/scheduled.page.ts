import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { ConfirmData, ConfirmDialog } from '../../../shared/confirm-dialog/confirm.dialog';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { centsToDecimal, MoneyInputDirective, toCents } from '../../../shared/money-input/money-input.directive';
import { OccurrenceStatus, ScheduledOccurrence } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { addMonths, currentMonth, isMonth, monthLabel, todayIso } from '../data/month.util';
import { RecurringPage } from './recurring.page';

type Acao = { id: string; modo: 'confirmar' | 'remarcar' };

const GRUPOS: { titulo: string; estados: OccurrenceStatus[] }[] = [
  { titulo: 'Atrasados', estados: ['OVERDUE'] },
  { titulo: 'Para hoje', estados: ['TO_CONFIRM'] },
  { titulo: 'Próximos', estados: ['EXPECTED', 'RESCHEDULED'] },
  { titulo: 'Resolvidos', estados: ['CONFIRMED', 'CANCELLED'] },
];

const ROTULOS: Record<OccurrenceStatus, string> = {
  EXPECTED: 'previsto',
  TO_CONFIRM: 'para confirmar',
  OVERDUE: 'atrasado',
  CONFIRMED: 'confirmado',
  RESCHEDULED: 'remarcado',
  CANCELLED: 'cancelado',
};

/**
 * Agendados (os antigos "fixos"): as ocorrências do mês agrupadas por situação, com "Recebi/Paguei"
 * (valor e data ajustáveis), "Remarcar" e "Não vou receber/pagar"; embaixo, as regras. O mês fica no
 * query param ?month=.
 */
@Component({
  selector: 'app-scheduled-page',
  imports: [
    BrlPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MoneyInputDirective,
    ReactiveFormsModule,
    RecurringPage,
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

    <div class="totais">
      <span>A receber <strong class="entra">{{ aReceber() | brl }}</strong></span>
      <span>A pagar <strong class="sai">{{ aPagar() | brl }}</strong></span>
    </div>

    @if (lista.isLoading() && !lista.hasValue()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (erro(); as mensagem) {
      <p class="erro" role="alert">{{ mensagem }}</p>
    }

    @for (grupo of grupos(); track grupo.titulo) {
      <section class="grupo" [attr.aria-label]="grupo.titulo">
        <h3 class="grupo__titulo">{{ grupo.titulo }}</h3>
        <ul class="lista">
          @for (o of grupo.itens; track o.id ?? o.recurringId) {
            <li class="item" [class.item--atrasado]="o.status === 'OVERDUE'" [class.item--resolvido]="!o.id || o.status === 'CONFIRMED'">
              <div class="item__linha">
                <span class="item__data">{{ dia(o.date) }}</span>
                <span class="item__texto">
                  <span class="item__titulo">{{ o.description ?? (o.type === 'INCOME' ? 'Recebimento' : 'Pagamento') }}</span>
                  <span class="item__detalhe">
                    {{ o.accountName }} · <span class="selo">{{ rotulos[o.status] }}</span>
                    @if (o.date !== o.scheduledDate) {
                      <span> · era {{ dia(o.scheduledDate) }}</span>
                    }
                  </span>
                </span>
                <span class="item__valor" [class.entra]="o.type === 'INCOME'" [class.sai]="o.type === 'EXPENSE'">
                  {{ (o.type === 'INCOME' ? o.amount : '-' + o.amount) | brl: true }}
                </span>
              </div>

              @if (o.id && aberto(o)) {
                @if (acao()?.id === o.id) {
                  @if (acao()!.modo === 'confirmar') {
                    <form class="acao" [formGroup]="confirmacao" (ngSubmit)="confirmar(o)">
                      <mat-form-field subscriptSizing="dynamic">
                        <mat-label>Valor</mat-label>
                        <input matInput appMoneyInput formControlName="amount" />
                      </mat-form-field>
                      <mat-form-field subscriptSizing="dynamic">
                        <mat-label>{{ o.type === 'INCOME' ? 'Caiu em' : 'Pago em' }}</mat-label>
                        <input matInput type="date" formControlName="date" [max]="hoje" />
                      </mat-form-field>
                      <button mat-flat-button type="submit" [disabled]="ocupado()">Confirmar</button>
                      <button mat-button type="button" (click)="acao.set(null)">Cancelar</button>
                    </form>
                  } @else {
                    <form class="acao" [formGroup]="remarcacao" (ngSubmit)="remarcar(o)">
                      <mat-form-field subscriptSizing="dynamic">
                        <mat-label>{{ o.type === 'INCOME' ? 'Vou receber em' : 'Vou pagar em' }}</mat-label>
                        <input matInput type="date" formControlName="date" [min]="amanha" />
                      </mat-form-field>
                      <button mat-flat-button type="submit" [disabled]="ocupado()">Remarcar</button>
                      <button mat-button type="button" (click)="acao.set(null)">Cancelar</button>
                    </form>
                  }
                } @else {
                  <div class="botoes">
                    <button mat-flat-button type="button" (click)="abrirConfirmacao(o)">
                      {{ o.type === 'INCOME' ? 'Recebi' : 'Paguei' }}
                    </button>
                    <button mat-stroked-button type="button" (click)="abrirRemarcacao(o)">Remarcar</button>
                    <button mat-button type="button" (click)="cancelar(o)">
                      {{ o.type === 'INCOME' ? 'Não vou receber' : 'Não vou pagar' }}
                    </button>
                  </div>
                }
              }
            </li>
          }
        </ul>
      </section>
    } @empty {
      @if (lista.hasValue()) {
        <p class="vazio">Nada agendado neste mês.</p>
      }
    }

    <section class="regras" aria-label="Regras">
      <h3 class="grupo__titulo">Regras</h3>
      <app-recurring-page (changed)="lista.reload()" />
    </section>
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
    .totais {
      display: flex;
      gap: 24px;
      margin: 8px 0 12px;
    }
    .entra {
      color: #2e7d32;
    }
    .sai,
    .erro {
      color: var(--mat-sys-error);
    }
    .grupo__titulo {
      font: var(--mat-sys-title-small);
      margin: 16px 0 6px;
    }
    .lista {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .item {
      padding: 8px 12px;
      margin-bottom: 6px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-low);
    }
    .item--atrasado {
      border-left: 4px solid var(--mat-sys-error);
    }
    .item--resolvido {
      opacity: 0.7;
    }
    .item__linha {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .item__data {
      width: 44px;
      color: var(--mat-sys-on-surface-variant);
    }
    .item__texto {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .item__detalhe {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .item__valor {
      font-weight: 600;
    }
    .selo {
      font-weight: 600;
    }
    .botoes,
    .acao {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      margin-top: 8px;
    }
    .vazio {
      padding: 16px 0;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
    .regras {
      margin-top: 24px;
    }
  `,
})
export class ScheduledPage {
  private readonly finance = inject(FinanceService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  /** Query param ?month=2026-10. */
  readonly month = input<string>();
  protected readonly mes = computed(() => (isMonth(this.month()) ? this.month()! : currentMonth()));
  protected readonly rotuloMes = computed(() => monthLabel(this.mes()));
  protected readonly rotulos = ROTULOS;
  protected readonly hoje = todayIso();
  protected readonly amanha = todayIso(new Date(Date.now() + 86_400_000));

  protected readonly lista = rxResource({ params: this.mes, stream: ({ params }) => this.finance.scheduled(params) });

  protected readonly grupos = computed(() => {
    const itens = this.lista.value() ?? [];
    return GRUPOS.map((g) => ({ titulo: g.titulo, itens: itens.filter((o) => g.estados.includes(o.status)) })).filter(
      (g) => g.itens.length > 0,
    );
  });

  /** Soma em centavos do que ainda está em aberto. */
  protected readonly aReceber = computed(() => this.somaAberta('INCOME'));
  protected readonly aPagar = computed(() => this.somaAberta('EXPENSE'));

  protected readonly acao = signal<Acao | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly erro = computed(() =>
    this.lista.error() ? problemMessage(this.lista.error(), 'Não foi possível carregar os agendados.') : null,
  );

  protected readonly confirmacao = this.fb.group({
    amount: [null as string | null, Validators.required],
    date: [todayIso(), Validators.required],
  });
  protected readonly remarcacao = this.fb.group({ date: ['', Validators.required] });

  protected aberto(o: ScheduledOccurrence): boolean {
    return o.status !== 'CONFIRMED' && o.status !== 'CANCELLED';
  }

  protected dia(iso: string): string {
    return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
  }

  protected mudarMes(delta: number): void {
    this.acao.set(null);
    this.router.navigate([], { queryParams: { month: addMonths(this.mes(), delta) }, queryParamsHandling: 'merge' });
  }

  protected abrirConfirmacao(o: ScheduledOccurrence): void {
    // Data sugerida: o dia previsto (atrasado caiu nele, provavelmente); confirmando adiantado, hoje
    this.confirmacao.reset({ amount: o.amount, date: o.date <= this.hoje ? o.date : this.hoje });
    this.acao.set({ id: o.id!, modo: 'confirmar' });
  }

  protected abrirRemarcacao(o: ScheduledOccurrence): void {
    this.remarcacao.reset({ date: o.date > this.hoje ? o.date : this.amanha });
    this.acao.set({ id: o.id!, modo: 'remarcar' });
  }

  async confirmar(o: ScheduledOccurrence): Promise<void> {
    const v = this.confirmacao.getRawValue();
    if (!v.amount || !v.date) {
      return;
    }
    await this.executar(async () => {
      await firstValueFrom(this.finance.confirmOccurrence(o.id!, { amount: v.amount!, date: v.date! }));
    });
  }

  async remarcar(o: ScheduledOccurrence): Promise<void> {
    const date = this.remarcacao.getRawValue().date;
    if (!date) {
      return;
    }
    await this.executar(async () => {
      const { warning } = await firstValueFrom(this.finance.rescheduleOccurrence(o.id!, date));
      if (warning) {
        this.snackBar.open(warning, 'OK', { duration: 6000 });
      }
    });
  }

  async cancelar(o: ScheduledOccurrence): Promise<void> {
    const recebimento = o.type === 'INCOME';
    const confirmou = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data: {
            title: recebimento ? 'Não vai receber este mês?' : 'Não vai pagar este mês?',
            message: `${o.description ?? 'Este agendado'} sai deste mês. A regra continua valendo para os próximos.`,
            confirmLabel: recebimento ? 'Não vou receber' : 'Não vou pagar',
          },
        })
        .afterClosed(),
    );
    if (confirmou) {
      await this.executar(async () => {
        await firstValueFrom(this.finance.skipOccurrence(o.id!));
      });
    }
  }

  private async executar(acao: () => Promise<void>): Promise<void> {
    this.ocupado.set(true);
    try {
      await acao();
      this.acao.set(null);
      this.lista.reload();
    } catch (error) {
      this.snackBar.open(problemMessage(error, 'Não foi possível concluir.'), 'OK', { duration: 5000 });
    } finally {
      this.ocupado.set(false);
    }
  }

  private somaAberta(tipo: 'INCOME' | 'EXPENSE'): string {
    return centsToDecimal(
      (this.lista.value() ?? [])
        .filter((o) => o.type === tipo && this.aberto(o))
        .reduce((soma, o) => soma + toCents(o.amount), 0n),
    );
  }
}
