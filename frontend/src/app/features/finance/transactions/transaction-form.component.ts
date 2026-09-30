import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Account, Category } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { todayIso } from '../data/month.util';

export type FormMode = 'EXPENSE' | 'INCOME' | 'TRANSFER';

type FormValue = ReturnType<TransactionFormComponent['form']['getRawValue']>;

/**
 * Lançamento rápido: valor, descrição, categoria, conta e data. No modo completo também faz
 * transferência entre contas e marca "pago". `compact` (tela Hoje) mostra só gasto e renda.
 * Depois de salvar, limpa valor e descrição e avisa com `saved`.
 */
@Component({
  selector: 'app-transaction-form',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MoneyInputDirective,
  ],
  template: `
    <form class="form" [class.form--compacto]="compact()" [formGroup]="form" (ngSubmit)="save()">
      <mat-button-toggle-group class="tipo" formControlName="mode" aria-label="Tipo de lançamento" hideSingleSelectionIndicator>
        <mat-button-toggle value="EXPENSE">Gasto</mat-button-toggle>
        <mat-button-toggle value="INCOME">Renda</mat-button-toggle>
        @if (!compact()) {
          <mat-button-toggle value="TRANSFER">Transferência</mat-button-toggle>
        }
      </mat-button-toggle-group>

      <div class="campos">
        <mat-form-field class="valor">
          <mat-label>Valor</mat-label>
          <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
        </mat-form-field>

        <mat-form-field class="descricao">
          <mat-label>Descrição</mat-label>
          <input matInput formControlName="description" maxlength="200" />
        </mat-form-field>

        @if (modo() !== 'TRANSFER') {
          <mat-form-field>
            <mat-label>Categoria</mat-label>
            <mat-select formControlName="categoryId">
              @for (categoria of categoriasDoTipo(); track categoria.id) {
                <mat-option [value]="categoria.id">{{ categoria.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        <mat-form-field>
          <mat-label>{{ modo() === 'TRANSFER' ? 'De' : 'Conta' }}</mat-label>
          <mat-select formControlName="accountId">
            @for (conta of accounts(); track conta.id) {
              <mat-option [value]="conta.id">{{ conta.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (modo() === 'TRANSFER') {
          <mat-form-field>
            <mat-label>Para</mat-label>
            <mat-select formControlName="toAccountId">
              @for (conta of accounts(); track conta.id) {
                <mat-option [value]="conta.id" [disabled]="conta.id === form.controls.accountId.value">{{ conta.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        <mat-form-field class="data">
          <mat-label>Data</mat-label>
          <input matInput type="date" formControlName="date" />
        </mat-form-field>

        @if (!compact() && modo() !== 'TRANSFER') {
          <mat-checkbox formControlName="paid">Pago</mat-checkbox>
        }

        <button mat-flat-button type="submit" [disabled]="saving()">Lançar</button>
      </div>

      @if (error(); as mensagem) {
        <p class="erro" role="alert">{{ mensagem }}</p>
      }
    </form>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .tipo {
      align-self: flex-start;
    }
    .campos {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 12px;
    }
    .campos mat-form-field {
      flex: 1 1 160px;
    }
    .campos .descricao {
      flex: 2 1 220px;
    }
    .erro {
      color: var(--mat-sys-error);
      margin: 0;
    }
  `,
})
export class TransactionFormComponent {
  private readonly finance = inject(FinanceService);
  private readonly fb = inject(FormBuilder);

  readonly accounts = input.required<Account[]>();
  readonly categories = input.required<Category[]>();
  /** Versão enxuta (tela Hoje): sem transferência e sem "pago". */
  readonly compact = input(false);
  readonly saved = output<void>();

  readonly form = this.fb.group({
    mode: this.fb.control<FormMode>('EXPENSE', { nonNullable: true }),
    amount: this.fb.control<string | null>(null, Validators.required),
    description: this.fb.control('', { nonNullable: true }),
    categoryId: this.fb.control<string | null>(null),
    accountId: this.fb.control<string | null>(null, Validators.required),
    toAccountId: this.fb.control<string | null>(null),
    date: this.fb.control(todayIso(), { nonNullable: true, validators: Validators.required }),
    paid: this.fb.control(true, { nonNullable: true }),
  });

  protected readonly modo = toSignal(this.form.controls.mode.valueChanges, { initialValue: this.form.controls.mode.value });

  protected readonly categoriasDoTipo = computed(() => {
    const kind = this.modo() === 'INCOME' ? 'INCOME' : 'EXPENSE';
    return this.categories().filter((c) => c.kind === kind);
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Primeira conta já vem escolhida; ao trocar gasto/renda, a categoria do outro tipo sai
    effect(() => {
      const contas = this.accounts();
      if (!this.form.controls.accountId.value && contas.length > 0) {
        this.form.controls.accountId.setValue(contas[0].id);
      }
    });
    effect(() => {
      const validas = this.categoriasDoTipo();
      const atual = this.form.controls.categoryId.value;
      if (atual && !validas.some((c) => c.id === atual)) {
        this.form.controls.categoryId.setValue(null);
      }
    });
  }

  async save(): Promise<void> {
    const v = this.form.getRawValue();
    const faltando = this.faltando(v);
    if (faltando || this.saving()) {
      this.error.set(faltando);
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const description = v.description.trim() || null;
    try {
      if (v.mode === 'TRANSFER') {
        await firstValueFrom(
          this.finance.createTransfer({
            fromAccountId: v.accountId!,
            toAccountId: v.toAccountId!,
            amount: v.amount!,
            date: v.date,
            description,
          }),
        );
      } else {
        await firstValueFrom(
          this.finance.createTransaction({
            type: v.mode,
            amount: v.amount!,
            date: v.date,
            description,
            accountId: v.accountId!,
            categoryId: v.categoryId!,
            paid: this.compact() ? true : v.paid,
            tagIds: [],
          }),
        );
      }
      this.form.patchValue({ amount: null, description: '' });
      this.form.markAsUntouched();
      this.saved.emit();
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível lançar.'));
    } finally {
      this.saving.set(false);
    }
  }

  private faltando(v: FormValue): string | null {
    if (!v.amount || Number(v.amount) <= 0) return 'Informe o valor.';
    if (!v.accountId) return 'Escolha a conta.';
    if (v.mode === 'TRANSFER') {
      if (!v.toAccountId) return 'Escolha a conta de destino.';
      if (v.toAccountId === v.accountId) return 'Escolha contas diferentes.';
    } else if (!v.categoryId) {
      return 'Escolha a categoria.';
    }
    return null;
  }
}
