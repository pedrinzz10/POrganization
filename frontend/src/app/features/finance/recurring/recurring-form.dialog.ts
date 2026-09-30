import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom, startWith } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Recurring, RecurringRequest } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';
import { currentMonth } from '../data/month.util';

export interface RecurringFormData {
  /** Fixo a editar; sem ele, cria um novo. */
  recurring?: Recurring;
}

/**
 * Gasto ou renda fixo: valor, dia do mês e onde cai (conta, ou cartão para gasto). Editar só
 * muda os meses que ainda não foram gerados.
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
    <h2 mat-dialog-title>{{ editing ? 'Editar fixo' : 'Novo fixo' }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="recurring-form">
        <mat-button-toggle-group formControlName="type" aria-label="Tipo" hideSingleSelectionIndicator>
          <mat-button-toggle value="EXPENSE">Gasto</mat-button-toggle>
          <mat-button-toggle value="INCOME">Renda</mat-button-toggle>
        </mat-button-toggle-group>
        <mat-form-field>
          <mat-label>Descrição</mat-label>
          <input matInput formControlName="description" maxlength="200" placeholder="Ex.: Aluguel" />
        </mat-form-field>
        <div class="linha">
          <mat-form-field>
            <mat-label>Valor</mat-label>
            <input matInput appMoneyInput formControlName="amount" placeholder="R$ 0,00" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Dia do mês</mat-label>
            <input matInput type="number" min="1" max="31" formControlName="dayOfMonth" />
            <mat-hint>31 cai no último dia em meses curtos</mat-hint>
          </mat-form-field>
        </div>
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
            @for (conta of contas.value() ?? []; track conta.id) {
              <mat-option [value]="'conta:' + conta.id">{{ conta.name }}</mat-option>
            }
            @if (tipo() === 'EXPENSE') {
              @for (cartao of cartoes.value() ?? []; track cartao.id) {
                <mat-option [value]="'cartao:' + cartao.id">Cartão {{ cartao.name }}</mat-option>
              }
            }
          </mat-select>
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
        @if (error(); as mensagem) {
          <p class="erro" role="alert">{{ mensagem }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions>
      @if (editing) {
        <button mat-button type="button" class="perigo" [disabled]="saving()" (click)="remove()">Excluir</button>
      }
      <span class="espaco"></span>
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="recurring-form" [disabled]="saving()">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
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
  private readonly data = inject<RecurringFormData>(MAT_DIALOG_DATA);
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
    dayOfMonth: [this.editing?.dayOfMonth ?? 10, [Validators.required, Validators.min(1), Validators.max(31)]],
    categoryId: [this.editing?.categoryId ?? (null as string | null), Validators.required],
    /** "conta:<id>" ou "cartao:<id>" */
    target: [
      this.editing ? (this.editing.cardId ? `cartao:${this.editing.cardId}` : `conta:${this.editing.accountId}`) : (null as string | null),
      Validators.required,
    ],
    startMonth: [this.editing?.startMonth ?? currentMonth(), Validators.required],
    endMonth: [this.editing?.endMonth ?? ''],
  });

  protected readonly tipo = toSignal(this.form.controls.type.valueChanges.pipe(startWith(this.form.controls.type.value)), {
    requireSync: true,
  });
  protected readonly categoriasDoTipo = computed(() =>
    (this.categorias.value() ?? []).filter((c) => c.kind === (this.tipo() === 'INCOME' ? 'INCOME' : 'EXPENSE')),
  );

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      this.error.set('Preencha valor, dia, categoria, onde cai e o mês de início.');
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
      dayOfMonth: Number(v.dayOfMonth),
      startMonth: v.startMonth!,
      endMonth: v.endMonth || null,
    };
    await this.run(() =>
      firstValueFrom(this.editing ? this.finance.updateRecurring(this.editing.id, request) : this.finance.createRecurring(request)),
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
