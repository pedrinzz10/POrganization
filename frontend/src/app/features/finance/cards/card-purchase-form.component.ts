import { Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { BrlPipe } from '../../../shared/money-input/brl.pipe';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Card, Category } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { todayIso } from '../data/month.util';
import { splitInstallments } from './installments';

/**
 * Compra no cartão, à vista ou em até 48x. A prévia mostra as parcelas com a mesma conta da API
 * (resto na primeira), então o que aparece aqui é o que vai para as faturas.
 */
@Component({
  selector: 'app-card-purchase-form',
  imports: [BrlPipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MoneyInputDirective],
  template: `
    <form class="form" [formGroup]="form" (ngSubmit)="save()">
      <div class="campos">
        <mat-form-field>
          <mat-label>Valor total</mat-label>
          <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Parcelas</mat-label>
          <mat-select formControlName="installments">
            @for (n of opcoesParcelas; track n) {
              <mat-option [value]="n">{{ n === 1 ? 'À vista' : n + 'x' }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field class="descricao">
          <mat-label>Descrição</mat-label>
          <input matInput formControlName="description" maxlength="150" placeholder="Ex.: Loja" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Categoria</mat-label>
          <mat-select formControlName="categoryId">
            @for (categoria of categoriasDeGasto(); track categoria.id) {
              <mat-option [value]="categoria.id">{{ categoria.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Data da compra</mat-label>
          <input matInput type="date" formControlName="date" />
        </mat-form-field>
      </div>

      @if (previa().length > 1) {
        <div class="previa" aria-label="Prévia das parcelas">
          <span class="previa__titulo">{{ previa().length }} parcelas:</span>
          <ol class="previa__lista">
            @for (valor of previa(); track $index) {
              <li class="parcela">{{ $index + 1 }}ª {{ valor | brl }}</li>
            }
          </ol>
        </div>
      } @else if (valorInvalidoParaParcelas()) {
        <p class="erro">Valor pequeno demais para {{ parcelas() }} parcelas.</p>
      }

      @if (error(); as mensagem) {
        <p class="erro" role="alert">{{ mensagem }}</p>
      }
      <div class="acoes">
        <button mat-button type="button" (click)="cancelled.emit()">Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Lançar compra</button>
      </div>
    </form>
  `,
  styles: `
    .campos {
      display: flex;
      flex-wrap: wrap;
      gap: 0 12px;
    }
    .campos mat-form-field {
      flex: 1 1 150px;
    }
    .campos .descricao {
      flex: 2 1 200px;
    }
    .previa {
      margin: 0 0 8px;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .previa__lista {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 12px;
      list-style: none;
      padding: 0;
      margin: 4px 0 0;
    }
    .erro {
      color: var(--mat-sys-error);
    }
    .acoes {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
  `,
})
export class CardPurchaseFormComponent {
  private readonly finance = inject(FinanceService);
  private readonly fb = inject(FormBuilder);

  readonly card = input.required<Card>();
  readonly categories = input.required<Category[]>();
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly opcoesParcelas = Array.from({ length: 48 }, (_, i) => i + 1);

  readonly form = this.fb.group({
    amount: this.fb.control<string | null>(null, Validators.required),
    installments: this.fb.control(1, { nonNullable: true }),
    description: this.fb.control('', { nonNullable: true }),
    categoryId: this.fb.control<string | null>(null, Validators.required),
    date: this.fb.control(todayIso(), { nonNullable: true, validators: Validators.required }),
  });

  private readonly valor = toSignal(this.form.valueChanges.pipe(startWith(this.form.value)), { requireSync: true });
  protected readonly parcelas = computed(() => this.valor().installments ?? 1);
  protected readonly previa = computed(() => splitInstallments(this.valor().amount ?? null, this.parcelas()));
  protected readonly valorInvalidoParaParcelas = computed(() => !!this.valor().amount && this.previa().length === 0);

  protected readonly categoriasDeGasto = computed(() => this.categories().filter((c) => c.kind === 'EXPENSE'));

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  async save(): Promise<void> {
    const v = this.form.getRawValue();
    const faltando = !v.amount
      ? 'Informe o valor.'
      : !v.categoryId
        ? 'Escolha a categoria.'
        : this.previa().length === 0
          ? 'Valor pequeno demais para as parcelas.'
          : null;
    if (faltando || this.saving()) {
      this.error.set(faltando);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(
        this.finance.purchase(this.card().id, {
          amount: v.amount!,
          date: v.date,
          description: v.description.trim() || null,
          categoryId: v.categoryId!,
          installments: v.installments,
        }),
      );
      this.form.patchValue({ amount: null, description: '', installments: 1 });
      this.saved.emit();
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível lançar a compra.'));
    } finally {
      this.saving.set(false);
    }
  }
}
