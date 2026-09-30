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
import { Goal, GoalRequest } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';

export interface GoalFormData {
  /** Meta a editar; sem ela, cria uma nova. */
  goal?: Goal;
}

/** Criar ou editar meta: nome, valor-alvo, prazo e (opcional) a conta onde o dinheiro fica. */
@Component({
  selector: 'app-goal-form-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MoneyInputDirective],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Editar meta' : 'Nova meta' }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="goal-form">
        <mat-form-field>
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" maxlength="80" placeholder="Ex.: Reserva de emergência" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Quanto guardar</mat-label>
          <input matInput appMoneyInput formControlName="targetAmount" placeholder="R$ 0,00" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Prazo</mat-label>
          <input matInput type="date" formControlName="targetDate" />
          <mat-hint>Opcional: com prazo, mostra quanto guardar por mês</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Conta</mat-label>
          <mat-select formControlName="accountId">
            <mat-option [value]="null">Nenhuma</mat-option>
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
    <mat-dialog-actions>
      @if (editing) {
        <button mat-button type="button" class="perigo" [disabled]="saving()" (click)="remove()">Excluir</button>
        <button mat-button type="button" [disabled]="saving()" (click)="archive()">Arquivar</button>
      }
      <span class="espaco"></span>
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="goal-form" [disabled]="saving()">Salvar</button>
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
    .erro,
    .perigo {
      color: var(--mat-sys-error);
    }
    .espaco {
      flex: 1;
    }
  `,
})
export class GoalFormDialog {
  private readonly data = inject<GoalFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<GoalFormDialog, boolean>>(MatDialogRef);
  private readonly finance = inject(FinanceService);

  protected readonly editing = this.data.goal;
  protected readonly contas = rxResource({ stream: () => this.finance.listAccounts() });

  readonly form = inject(FormBuilder).group({
    name: [this.editing?.name ?? '', [Validators.required, Validators.pattern(/\S/)]],
    targetAmount: [this.editing?.targetAmount ?? (null as string | null), Validators.required],
    targetDate: [this.editing?.targetDate ?? ''],
    accountId: [this.editing?.accountId ?? (null as string | null)],
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      this.error.set('Informe o nome e quanto guardar.');
      return;
    }
    await this.run(() => {
      const request = this.request(false);
      return firstValueFrom(this.editing ? this.finance.updateGoal(this.editing.id, request) : this.finance.createGoal(request));
    });
  }

  async archive(): Promise<void> {
    await this.run(() => firstValueFrom(this.finance.updateGoal(this.editing!.id, this.request(true))));
  }

  async remove(): Promise<void> {
    await this.run(() => firstValueFrom(this.finance.deleteGoal(this.editing!.id)));
  }

  private request(archived: boolean): GoalRequest {
    const v = this.form.getRawValue();
    return {
      name: (v.name ?? '').trim(),
      targetAmount: v.targetAmount!,
      targetDate: v.targetDate || null,
      accountId: v.accountId,
      archived,
    };
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
