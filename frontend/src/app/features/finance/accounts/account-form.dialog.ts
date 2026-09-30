import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { problemMessage } from '../../../core/http/problem';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { Account, ACCOUNT_TYPE_LABELS, AccountRequest, AccountType } from '../data/finance.model';
import { FinanceService } from '../data/finance.service';

export interface AccountFormData {
  /** Conta a editar; sem ela, cria uma nova. */
  account?: Account;
}

/** Criar ou editar conta. Editando, também arquiva ou exclui (a API recusa excluir conta com lançamentos). */
@Component({
  selector: 'app-account-form-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MoneyInputDirective],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Editar conta' : 'Nova conta' }}</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" id="account-form">
        <mat-form-field>
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" placeholder="Ex.: Nubank" maxlength="100" />
          @if (form.controls.name.invalid) {
            <mat-error>Informe o nome.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Tipo</mat-label>
          <mat-select formControlName="type">
            @for (tipo of tipos; track tipo) {
              <mat-option [value]="tipo">{{ rotulos[tipo] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Saldo inicial</mat-label>
          <input matInput appMoneyInput formControlName="initialBalance" placeholder="R$ 0,00" />
          <mat-hint>Quanto havia na conta antes do primeiro lançamento</mat-hint>
        </mat-form-field>
        @if (error(); as mensagem) {
          <p class="erro" role="alert">{{ mensagem }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions>
      @if (editing) {
        <button mat-button type="button" class="perigo" [disabled]="saving()" (click)="remove()">Excluir</button>
        <button mat-button type="button" [disabled]="saving()" (click)="archive()">
          {{ editing.archived ? 'Desarquivar' : 'Arquivar' }}
        </button>
      }
      <span class="espaco"></span>
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="account-form" [disabled]="saving()">Salvar</button>
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
export class AccountFormDialog {
  private readonly data = inject<AccountFormData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<AccountFormDialog, boolean>>(MatDialogRef);
  private readonly finance = inject(FinanceService);

  protected readonly editing = this.data.account;
  protected readonly tipos = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];
  protected readonly rotulos = ACCOUNT_TYPE_LABELS;

  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.editing?.name ?? '', [Validators.required, Validators.pattern(/\S/)]],
    type: [this.editing?.type ?? ('CHECKING' as AccountType)],
    initialBalance: [this.editing?.initialBalance ?? '0.00'],
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: AccountRequest = { name: v.name.trim(), type: v.type, initialBalance: v.initialBalance || '0.00' };
    await this.run(() =>
      firstValueFrom(this.editing ? this.finance.updateAccount(this.editing.id, request) : this.finance.createAccount(request)),
    );
  }

  async archive(): Promise<void> {
    await this.run(() => firstValueFrom(this.finance.setAccountArchived(this.editing!.id, !this.editing!.archived)));
  }

  async remove(): Promise<void> {
    await this.run(() => firstValueFrom(this.finance.deleteAccount(this.editing!.id)));
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
