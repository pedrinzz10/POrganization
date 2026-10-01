import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  catchError,
  debounceTime,
  firstValueFrom,
  map,
  merge,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import {
  Adjustment,
  Recurring,
  RecurringRequest,
  RulePreview,
  ScheduleRule,
} from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { currentMonth } from '../data/month.util';

export interface RecurringFormData {
  /** Fixo a editar; sem ele, cria um novo. */
  recurring?: Recurring;
  /** Nova assinatura já neste cartão (gasto, aberto pela tela Cartões). */
  cardId?: string;
}

/**
 * Agendado (gasto ou renda que se repete): valor, regra da data e onde cai (conta, ou cartão para
 * gasto). Editar muda também as ocorrências em aberto; as já confirmadas ficam como foram.
 * Gasto no cartão é uma assinatura: o título e os exemplos mudam, e a tela Cartões abre o
 * formulário já com o cartão escolhido.
 */
@Component({
  selector: 'app-recurring-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MoneyInputDirective,
  ],
  template: `
    <h2 mat-dialog-title>{{ titulo() }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="recurring-form">
        @if (!data.cardId) {
          <mat-button-toggle-group
            formControlName="type"
            aria-label="Tipo"
            hideSingleSelectionIndicator
          >
            <mat-button-toggle value="EXPENSE">Gasto</mat-button-toggle>
            <mat-button-toggle value="INCOME">Renda</mat-button-toggle>
          </mat-button-toggle-group>
        }
        <mat-form-field>
          <mat-label>Descrição</mat-label>
          <input
            matInput
            formControlName="description"
            maxlength="200"
            [placeholder]="assinatura() ? 'Ex.: Netflix, Spotify, iCloud' : 'Ex.: Aluguel'"
          />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Valor</mat-label>
          <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
        </mat-form-field>

        <mat-form-field>
          <mat-label>Quando</mat-label>
          <mat-select formControlName="ruleType">
            <mat-option value="DAY_OF_MONTH">Dia fixo do mês</mat-option>
            <mat-option value="BUSINESS_DAY">N-ésimo dia útil do mês</mat-option>
            <mat-option value="LAST_BUSINESS_DAY">Último dia útil do mês</mat-option>
          </mat-select>
        </mat-form-field>
        @switch (regra()) {
          @case ('DAY_OF_MONTH') {
            <div class="linha">
              <mat-form-field>
                <mat-label>Dia do mês</mat-label>
                <input matInput type="number" min="1" max="31" formControlName="dayOfMonth" />
                <mat-hint>31 cai no último dia em meses curtos</mat-hint>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Se cair em fim de semana ou feriado</mat-label>
                <mat-select formControlName="adjustment">
                  <mat-option value="KEEP">Mantém o dia</mat-option>
                  <mat-option value="ANTICIPATE">Antecipa para o dia útil anterior</mat-option>
                  <mat-option value="POSTPONE">Adia para o próximo dia útil</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
          }
          @case ('BUSINESS_DAY') {
            <mat-form-field>
              <mat-label>Qual dia útil</mat-label>
              <input matInput type="number" min="1" max="15" formControlName="businessDay" />
              <mat-hint
                >Ex.: 5 para o 5º dia útil (sem fins de semana e feriados nacionais)</mat-hint
              >
            </mat-form-field>
          }
        }
        @if (proximas().length > 0) {
          <p class="previa" aria-live="polite">Próximas datas: {{ proximas().join(' · ') }}</p>
        }
        <mat-form-field>
          <mat-label>Categoria</mat-label>
          <mat-select formControlName="categoryId">
            @for (c of categoriasDoTipo(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Onde cai</mat-label>
          <mat-select formControlName="target">
            <mat-optgroup label="Contas">
              @for (conta of contas.value() ?? []; track conta.id) {
                <mat-option [value]="'conta:' + conta.id">{{ conta.name }}</mat-option>
              }
            </mat-optgroup>
            @if (tipo() === 'EXPENSE' && cartoesAtivos().length) {
              <mat-optgroup label="Cartões de crédito (assinatura)">
                @for (cartao of cartoesAtivos(); track cartao.id) {
                  <mat-option [value]="'cartao:' + cartao.id">Cartão {{ cartao.name }}</mat-option>
                }
              </mat-optgroup>
            }
          </mat-select>
          @if (assinatura()) {
            <mat-hint>Cai todo mês na fatura do cartão, sem precisar confirmar.</mat-hint>
          } @else if (tipo() === 'EXPENSE' && cartoes.hasValue() && !cartoesAtivos().length) {
            <mat-hint>Assinatura no cartão? Cadastre o cartão em Finanças › Cartões.</mat-hint>
          }
        </mat-form-field>
        <div class="linha">
          <mat-form-field>
            <mat-label>Começa em</mat-label>
            <input matInput type="month" formControlName="startMonth" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Termina em</mat-label>
            <input matInput type="month" formControlName="endMonth" />
            <mat-hint>Vazio = sem fim</mat-hint>
          </mat-form-field>
        </div>
        @if (editing) {
          <p class="dica">
            A mudança vale também para o que ainda está em aberto; o que já foi confirmado não muda.
          </p>
        }
        @if (error(); as mensagem) {
          <p class="erro" role="alert">{{ mensagem }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions>
      @if (editing) {
        <button mat-button type="button" class="perigo" [disabled]="saving()" (click)="remove()">
          Excluir
        </button>
      }
      <span class="espaco"></span>
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="recurring-form" [disabled]="saving()">
        Salvar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .previa {
      margin: 0 0 8px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-primary);
    }
    .dica {
      margin: 0 0 8px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(420px, 80vw);
      padding-top: 8px;
    }
    .linha {
      display: flex;
      gap: 12px;
    }
    .linha mat-form-field {
      flex: 1;
    }
    .erro,
    .perigo {
      color: var(--mat-sys-error);
    }
    .espaco {
      flex: 1;
    }
  `,
})
export class RecurringFormDialog {
  protected readonly data = inject<RecurringFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<RecurringFormDialog, boolean>>(MatDialogRef);
  private readonly finance = inject(FinanceService);

  protected readonly editing = this.data.recurring;
  protected readonly contas = rxResource({ stream: () => this.finance.listAccounts() });
  protected readonly cartoes = rxResource({ stream: () => this.finance.listCards() });
  private readonly categorias = rxResource({ stream: () => this.finance.listCategories() });

  readonly form = inject(FormBuilder).group({
    type: [this.editing?.type ?? ('EXPENSE' as 'INCOME' | 'EXPENSE')],
    description: [this.editing?.description ?? ''],
    amount: [this.editing?.amount ?? (null as string | null), Validators.required],
    ruleType: [this.editing?.ruleType ?? ('DAY_OF_MONTH' as ScheduleRule)],
    // Assinatura nova: o dia de hoje costuma ser o dia da cobrança
    dayOfMonth: [
      this.editing?.dayOfMonth ?? ((this.data.cardId ? new Date().getDate() : 10) as number | null),
    ],
    businessDay: [this.editing?.businessDay ?? (5 as number | null)],
    adjustment: [this.editing?.adjustment ?? ('KEEP' as Adjustment)],
    categoryId: [this.editing?.categoryId ?? (null as string | null), Validators.required],
    /** "conta:<id>" ou "cartao:<id>" */
    target: [
      this.editing
        ? this.editing.cardId
          ? `cartao:${this.editing.cardId}`
          : `conta:${this.editing.accountId}`
        : this.data.cardId
          ? `cartao:${this.data.cardId}`
          : (null as string | null),
      Validators.required,
    ],
    startMonth: [this.editing?.startMonth ?? currentMonth(), Validators.required],
    endMonth: [this.editing?.endMonth ?? ''],
  });

  protected readonly tipo = toSignal(
    this.form.controls.type.valueChanges.pipe(startWith(this.form.controls.type.value)),
    {
      requireSync: true,
    },
  );
  private readonly destino = toSignal(
    this.form.controls.target.valueChanges.pipe(startWith(this.form.controls.target.value)),
    { requireSync: true },
  );
  /** Gasto no cartão = assinatura. */
  protected readonly assinatura = computed(
    () => this.tipo() === 'EXPENSE' && !!this.destino()?.startsWith('cartao:'),
  );
  protected readonly titulo = computed(() =>
    this.assinatura()
      ? this.editing
        ? 'Editar assinatura'
        : 'Nova assinatura'
      : this.editing
        ? 'Editar agendado'
        : 'Novo agendado',
  );
  protected readonly cartoesAtivos = computed(() =>
    (this.cartoes.value() ?? []).filter((c) => !c.archived || c.id === this.editing?.cardId),
  );

  protected readonly categoriasDoTipo = computed(() =>
    (this.categorias.value() ?? []).filter(
      (c) => c.kind === (this.tipo() === 'INCOME' ? 'INCOME' : 'EXPENSE'),
    ),
  );

  /** A regra escolhida decide quais campos aparecem e quais validações valem. */
  protected readonly regra = toSignal(
    this.form.controls.ruleType.valueChanges.pipe(startWith(this.form.controls.ruleType.value)),
    { requireSync: true },
  );

  /** Próximas datas calculadas pela API (dias úteis e feriados ficam no backend). */
  protected readonly proximas = signal<string[]>([]);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Validação dinâmica: só o campo da regra escolhida é obrigatório
    this.form.controls.ruleType.valueChanges
      .pipe(startWith(this.form.controls.ruleType.value), takeUntilDestroyed())
      .subscribe((regra) => {
        const { dayOfMonth, businessDay } = this.form.controls;
        dayOfMonth.setValidators(
          regra === 'DAY_OF_MONTH'
            ? [Validators.required, Validators.min(1), Validators.max(31)]
            : [],
        );
        businessDay.setValidators(
          regra === 'BUSINESS_DAY'
            ? [Validators.required, Validators.min(1), Validators.max(15)]
            : [],
        );
        dayOfMonth.updateValueAndValidity({ emitEvent: false });
        businessDay.updateValueAndValidity({ emitEvent: false });
      });
    // Prévia: recalcula quando a regra muda (e só quando os campos dela estão válidos)
    merge(
      this.form.controls.ruleType.valueChanges,
      this.form.controls.dayOfMonth.valueChanges,
      this.form.controls.businessDay.valueChanges,
      this.form.controls.adjustment.valueChanges,
    )
      .pipe(
        startWith(null),
        debounceTime(300),
        map(() => this.regraAtual()),
        switchMap((regra) =>
          regra
            ? this.finance.previewRule(regra).pipe(catchError(() => of({ nextDates: [] })))
            : of({ nextDates: [] }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ nextDates }) => this.proximas.set(nextDates.map(formatarData)));
  }

  private regraAtual(): RulePreview | null {
    const v = this.form.getRawValue();
    const regra = v.ruleType!;
    if (
      (regra === 'DAY_OF_MONTH' && this.form.controls.dayOfMonth.invalid) ||
      (regra === 'BUSINESS_DAY' && this.form.controls.businessDay.invalid)
    ) {
      return null;
    }
    return {
      ruleType: regra,
      dayOfMonth: regra === 'DAY_OF_MONTH' ? Number(v.dayOfMonth) : null,
      businessDay: regra === 'BUSINESS_DAY' ? Number(v.businessDay) : null,
      adjustment: regra === 'DAY_OF_MONTH' ? v.adjustment! : 'KEEP',
    };
  }

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      this.error.set('Preencha valor, quando cai, categoria, conta e o mês de início.');
      return;
    }
    const v = this.form.getRawValue();
    const [destino, id] = v.target!.split(':');
    const request: RecurringRequest = {
      type: v.type!,
      amount: v.amount!,
      description: v.description?.trim() || null,
      accountId: destino === 'conta' ? id : null,
      cardId: destino === 'cartao' ? id : null,
      categoryId: v.categoryId!,
      ...this.regraAtual()!,
      startMonth: v.startMonth!,
      endMonth: v.endMonth || null,
    };
    await this.run(() =>
      firstValueFrom(
        this.editing
          ? this.finance.updateRecurring(this.editing.id, request)
          : this.finance.createRecurring(request),
      ),
    );
  }

  async remove(): Promise<void> {
    await this.run(() => firstValueFrom(this.finance.deleteRecurring(this.editing!.id)));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await action();
      this.dialogRef.close(true);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}

/** "2026-10-07" → "07/10/2026". */
function formatarData(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
}
