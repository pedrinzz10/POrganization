import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Card, CardRequest } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';

export interface CardFormData {
  /** Cartão a editar; sem ele, cadastra um novo. */
  card?: Card;
}

/** Cadastro de cartão: limite, dia de fechamento, dia de vencimento e a conta que paga a fatura. */
@Component({
  selector: 'app-card-form-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MoneyInputDirective],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Editar cartão' : 'Novo cartão' }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="card-form">
        <mat-form-field>
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" placeholder="Ex.: Nubank" maxlength="60" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Limite</mat-label>
          <input matInput appMoneyInput formControlName="creditLimit" placeholder="R$ 0,00" />
        </mat-form-field>
        <div class="linha">
          <mat-form-field>
            <mat-label>Dia do fechamento</mat-label>
            <input matInput type="number" min="1" max="31" formControlName="closingDay" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Dia do vencimento</mat-label>
            <input matInput type="number" min="1" max="31" formControlName="dueDay" />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Conta que paga a fatura</mat-label>
          <mat-select formControlName="paymentAccountId">
            @for (conta of contas.value() ?? []; track conta.id) {
              <mat-option [value]="conta.id">{{ conta.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (error(); as mensagem) {
          <p class="erro" role="alert">{{ mensagem }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="card-form" [disabled]="saving()">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(380px, 80vw);
      padding-top: 8px;
    }
    .linha {
      display: flex;
      gap: 12px;
    }
    .linha mat-form-field {
      flex: 1;
    }
    .erro {
      color: var(--mat-sys-error);
    }
  `,
})
export class CardFormDialog {
  private readonly data = inject<CardFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CardFormDialog, boolean>>(MatDialogRef);
  private readonly finance = inject(FinanceService);

  protected readonly editing = this.data.card;
  protected readonly contas = rxResource({ stream: () => this.finance.listAccounts() });

  readonly form = inject(FormBuilder).group({
    name: [this.editing?.name ?? '', [Validators.required, Validators.pattern(/\S/)]],
    creditLimit: [this.editing?.creditLimit ?? (null as string | null), Validators.required],
    closingDay: [this.editing?.closingDay ?? 5, [Validators.required, Validators.min(1), Validators.max(31)]],
    dueDay: [this.editing?.dueDay ?? 12, [Validators.required, Validators.min(1), Validators.max(31)]],
    paymentAccountId: [this.editing?.paymentAccountId ?? (null as string | null), Validators.required],
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      this.error.set('Preencha nome, limite, dias (1 a 31) e a conta de pagamento.');
      return;
    }
    const v = this.form.getRawValue();
    const request: CardRequest = {
      name: v.name!.trim(),
      creditLimit: v.creditLimit!,
      closingDay: Number(v.closingDay),
      dueDay: Number(v.dueDay),
      paymentAccountId: v.paymentAccountId!,
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.editing ? this.finance.updateCard(this.editing.id, request) : this.finance.createCard(request));
      this.dialogRef.close(true);
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
